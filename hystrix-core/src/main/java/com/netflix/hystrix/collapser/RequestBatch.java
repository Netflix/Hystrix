package com.netflix.hystrix.collapser;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Observer;

import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.HystrixCollapser.CollapsedRequest;
import com.netflix.hystrix.HystrixCollapserProperties;

/**
 * A batch of requests collapsed together by a RequestCollapser instance. When full or time has expired it will execute and stop accepting further submissions.
 * 
 * @param <BatchReturnType>
 * @param <ResponseType>
 * @param <RequestArgumentType>
 */
public class RequestBatch<BatchReturnType, ResponseType, RequestArgumentType> {

    private static final Logger logger = LoggerFactory.getLogger(HystrixCollapser.class);

    private final HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser;
    final ConcurrentLinkedQueue<CollapsedRequest<ResponseType, RequestArgumentType>> requests = new ConcurrentLinkedQueue<CollapsedRequest<ResponseType, RequestArgumentType>>();
    // use AtomicInteger to count so we can use ConcurrentLinkedQueue instead of LinkedBlockingQueue
    private final AtomicInteger count = new AtomicInteger(0);
    private final int maxBatchSize;
    private final AtomicBoolean batchStarted = new AtomicBoolean();

    private ReentrantReadWriteLock batchLock = new ReentrantReadWriteLock();

    public RequestBatch(HystrixCollapserProperties properties, HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser, int maxBatchSize) {
        this.commandCollapser = commandCollapser;
        this.maxBatchSize = maxBatchSize;
    }

    /**
     * @return Observable if offer accepted, null if batch is full, already started or completed
     */
    public Observable<ResponseType> offer(RequestArgumentType arg) {
        /* short-cut - if the batch is started we reject the offer */
        if (batchStarted.get()) {
            return null;
        }

        /*
         * The 'read' just means non-exclusive even though we are writing.
         */
        if (batchLock.readLock().tryLock()) {
            try {
                /* double-check now that we have the lock - if the batch is started we reject the offer */
                if (batchStarted.get()) {
                    return null;
                }

                int s = count.incrementAndGet();
                if (s > maxBatchSize) {
                    return null;
                } else {
                    CollapsedRequestObservableFunction<ResponseType, RequestArgumentType> f = new CollapsedRequestObservableFunction<ResponseType, RequestArgumentType>(arg);
                    requests.add(f);
                    return Observable.create(f);
                }
            } finally {
                batchLock.readLock().unlock();
            }
        } else {
            return null;
        }
    }

    /**
     * Collapsed requests are triggered for batch execution and the array of arguments is passed in.
     * <p>
     * IMPORTANT IMPLEMENTATION DETAILS => The expected contract (responsibilities) of this method implementation is:
     * <p>
     * <ul>
     * <li>Do NOT block => Do the work on a separate worker thread. Do not perform inline otherwise it will block other requests.</li>
     * <li>Set ALL CollapsedRequest response values => Set the response values T on each CollapsedRequest<T, R>, even if the response is NULL otherwise the user thread waiting on the response will
     * think a response was never received and will either block indefinitely or will timeout while waiting.</li>
     * </ul>
     * 
     * @param args
     */
    public void executeBatchIfNotAlreadyStarted() {
        /*
         * - check that we only execute once since there's multiple paths to do so (timer, waiting thread or max batch size hit)
         * - close the gate so 'offer' can no longer be invoked and we turn those threads away so they create a new batch
         */
        if (batchStarted.compareAndSet(false, true)) {
            /* wait for 'offer' threads to finish before executing the batch so 'requests' is complete */
            batchLock.writeLock().lock();
            try {
                // shard batches
                Collection<Collection<CollapsedRequest<ResponseType, RequestArgumentType>>> shards = commandCollapser.shardRequests(requests);
                // for each shard execute its requests 
                for (final Collection<CollapsedRequest<ResponseType, RequestArgumentType>> shardRequests : shards) {
                    try {
                        // create a new command to handle this batch of requests
                        Observable<BatchReturnType> o = commandCollapser.createObservableCommand(shardRequests);
                        o.subscribe(new RequestBatch.BatchRequestObserver<ResponseType, RequestArgumentType, BatchReturnType>(commandCollapser, shardRequests));
                    } catch (Exception e) {
                        logger.error("Exception while creating and queueing command with batch.", e);
                        // if a failure occurs we want to pass that exception to all of the Futures that we've returned
                        for (CollapsedRequest<ResponseType, RequestArgumentType> request : shardRequests) {
                            try {
                                request.setException(e);
                            } catch (IllegalStateException e2) {
                                logger.debug("Failed trying to setException on CollapsedRequest", e2);
                            }
                        }
                    }
                }

            } catch (Exception e) {
                logger.error("Exception while sharding requests.", e);
                // same error handling as we do around the shards, but this is a wider net in case the shardRequest method fails
                for (CollapsedRequest<ResponseType, RequestArgumentType> request : requests) {
                    try {
                        request.setException(e);
                    } catch (IllegalStateException e2) {
                        logger.debug("Failed trying to setException on CollapsedRequest", e2);
                    }
                }
            } finally {
                batchLock.writeLock().unlock();
            }
        }
    }

    public void shutdown() {
        // take the 'batchStarted' state so offers and execution will not be triggered elsewhere
        if (batchStarted.compareAndSet(false, true)) {
            // get the write lock so offers are synced with this (we don't really need to unlock as this is a one-shot deal to shutdown)
            batchLock.writeLock().lock();
            try {
                // if we win the 'start' and once we have the lock we can now shut it down otherwise another thread will finish executing this batch
                if (requests.size() > 0) {
                    logger.warn("Requests still exist in queue but will not be executed due to RequestCollapser shutdown: " + requests.size(), new IllegalStateException());
                    /*
                     * In the event that there is a concurrency bug or thread scheduling prevents the timer from ticking we need to handle this so the Future.get() calls do not block.
                     * 
                     * I haven't been able to reproduce this use case on-demand but when stressing a machine saw this occur briefly right after the JVM paused (logs stopped scrolling).
                     * 
                     * This safety-net just prevents the CollapsedRequestFutureImpl.get() from waiting on the CountDownLatch until its max timeout.
                     */
                    for (CollapsedRequest<ResponseType, RequestArgumentType> request : requests) {
                        try {
                            ((CollapsedRequestObservableFunction<ResponseType, RequestArgumentType>) request).setExceptionIfResponseNotReceived(new IllegalStateException("Requests not executed before shutdown."));
                        } catch (Exception e) {
                            logger.debug("Failed to setException on CollapsedRequestFutureImpl instances.", e);
                        }
                        /**
                         * https://github.com/Netflix/Hystrix/issues/78 Include more info when collapsed requests remain in queue
                         */
                        logger.warn("Request still in queue but not be executed due to RequestCollapser shutdown. Argument => " + request.getArgument() + "   Request Object => " + request, new IllegalStateException());
                    }

                }
            } finally {
                batchLock.writeLock().unlock();
            }
        }
    }

    private static final class BatchRequestObserver<ResponseType, RequestArgumentType, BatchReturnType> implements Observer<BatchReturnType> {
        private final Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests;
        private final HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser;

        private BatchRequestObserver(HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser, Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests) {
            this.commandCollapser = commandCollapser;
            this.requests = requests;
        }

        @Override
        public void onCompleted() {
            // do nothing as we always expect onNext or onError to be called
        }

        @Override
        public void onError(Throwable t) {
            Exception e = null;
            if (t instanceof Exception) {
                e = (Exception) t;
            } else {
                // Hystrix 1.x uses Exception, not Throwable so to prevent a breaking change Throwable will be wrapped in Exception
                e = new Exception("Throwable caught while executing command with batch.", t);
            }
            logger.error("Exception while executing command with batch.", e);
            // if a failure occurs we want to pass that exception to all of the Futures that we've returned
            for (CollapsedRequest<ResponseType, RequestArgumentType> request : requests) {
                try {
                    request.setException(e);
                } catch (IllegalStateException e2) {
                    logger.debug("Failed trying to setException on CollapsedRequest", e2);
                }
            }
        }

        @Override
        public void onNext(BatchReturnType response) {
            try {
                commandCollapser.mapResponseToRequests(response, requests);
            } catch (Throwable e) {
                // handle Throwable in case anything is thrown so we don't block Observers waiting for onError/onCompleted
                Exception ee = null;
                if (e instanceof Exception) {
                    ee = (Exception) e;
                } else {
                    ee = new RuntimeException("Throwable caught while invoking 'mapResponseToRequests'", e);
                }
                logger.error("Exception mapping responses to requests.", e);
                // if a failure occurs we want to pass that exception to all of the Futures that we've returned
                for (CollapsedRequest<ResponseType, RequestArgumentType> request : requests) {
                    try {
                        ((CollapsedRequestObservableFunction<ResponseType, RequestArgumentType>) request).setExceptionIfResponseNotReceived(ee);
                    } catch (IllegalStateException e2) {
                        // if we have partial responses set in mapResponseToRequests
                        // then we may get IllegalStateException as we loop over them
                        // so we'll log but continue to the rest
                        logger.error("Partial success of 'mapResponseToRequests' resulted in IllegalStateException while setting Exception. Continuing ... ", e2);
                    }
                }
            }

            // check that all requests had setResponse or setException invoked in case 'mapResponseToRequests' was implemented poorly
            IllegalStateException ie = new IllegalStateException("No response set by " + commandCollapser.getCollapserKey().name() + " 'mapResponseToRequests' implementation.");
            for (CollapsedRequest<ResponseType, RequestArgumentType> request : requests) {
                try {
                    ((CollapsedRequestObservableFunction<ResponseType, RequestArgumentType>) request).setExceptionIfResponseNotReceived(ie);
                } catch (IllegalStateException e2) {
                    logger.debug("Partial success of 'mapResponseToRequests' resulted in IllegalStateException while setting 'No response set' Exception. Continuing ... ", e2);
                }
            }
        }
    }

}