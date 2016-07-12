/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.collapser;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;

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

    private static final Logger logger = LoggerFactory.getLogger(RequestBatch.class);

    private final HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser;
    private final int maxBatchSize;
    private final AtomicBoolean batchStarted = new AtomicBoolean();

    private final ConcurrentMap<RequestArgumentType, CollapsedRequest<ResponseType, RequestArgumentType>> argumentMap =
            new ConcurrentHashMap<RequestArgumentType, CollapsedRequest<ResponseType, RequestArgumentType>>();
    private final HystrixCollapserProperties properties;

    private ReentrantReadWriteLock batchLock = new ReentrantReadWriteLock();

    public RequestBatch(HystrixCollapserProperties properties, HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser, int maxBatchSize) {
        this.properties = properties;
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

                if (argumentMap.size() >= maxBatchSize) {
                    return null;
                } else {
                    CollapsedRequestSubject<ResponseType, RequestArgumentType> collapsedRequest =
                            new CollapsedRequestSubject<ResponseType, RequestArgumentType>(arg, this);
                    final CollapsedRequestSubject<ResponseType, RequestArgumentType> existing = (CollapsedRequestSubject<ResponseType, RequestArgumentType>) argumentMap.putIfAbsent(arg, collapsedRequest);
                    /**
                     * If the argument already exists in the batch, then there are 2 options:
                     * A) If request caching is ON (the default): only keep 1 argument in the batch and let all responses
                     * be hooked up to that argument
                     * B) If request caching is OFF: return an error to all duplicate argument requests
                     *
                     * This maintains the invariant that each batch has no duplicate arguments.  This prevents the impossible
                     * logic (in a user-provided mapResponseToRequests for HystrixCollapser and the internals of HystrixObservableCollapser)
                     * of trying to figure out which argument of a set of duplicates should get attached to a response.
                     *
                     * See https://github.com/Netflix/Hystrix/pull/1176 for further discussion.
                     */
                    if (existing != null) {
                        boolean requestCachingEnabled = properties.requestCacheEnabled().get();
                        if (requestCachingEnabled) {
                            return existing.toObservable();
                        } else {
                            return Observable.error(new IllegalArgumentException("Duplicate argument in collapser batch : [" + arg + "]  This is not supported.  Please turn request-caching on for HystrixCollapser:" + commandCollapser.getCollapserKey().name() + " or prevent duplicates from making it into the batch!"));
                        }
                    } else {
                        return collapsedRequest.toObservable();
                    }

                }
            } finally {
                batchLock.readLock().unlock();
            }
        } else {
            return null;
        }
    }

    /**
     * Best-effort attempt to remove an argument from a batch.  This may get invoked when a cancellation occurs somewhere downstream.
     * This method finds the argument in the batch, and removes it.
     *
     * @param arg argument to remove from batch
     */
    /* package-private */ void remove(RequestArgumentType arg) {
        if (batchStarted.get()) {
            //nothing we can do
            return;
        }

        if (batchLock.readLock().tryLock()) {
            try {
                /* double-check now that we have the lock - if the batch is started, deleting is useless */
                if (batchStarted.get()) {
                    return;
                }

                argumentMap.remove(arg);
            } finally {
                batchLock.readLock().unlock();
            }
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
     */
    public void executeBatchIfNotAlreadyStarted() {
        /*
         * - check that we only execute once since there's multiple paths to do so (timer, waiting thread or max batch size hit)
         * - close the gate so 'offer' can no longer be invoked and we turn those threads away so they create a new batch
         */
        if (batchStarted.compareAndSet(false, true)) {
            /* wait for 'offer'/'remove' threads to finish before executing the batch so 'requests' is complete */
            batchLock.writeLock().lock();

            try {
                // shard batches
                Collection<Collection<CollapsedRequest<ResponseType, RequestArgumentType>>> shards = commandCollapser.shardRequests(argumentMap.values());
                // for each shard execute its requests 
                for (final Collection<CollapsedRequest<ResponseType, RequestArgumentType>> shardRequests : shards) {
                    try {
                        // create a new command to handle this batch of requests
                        Observable<BatchReturnType> o = commandCollapser.createObservableCommand(shardRequests);

                        commandCollapser.mapResponseToRequests(o, shardRequests).doOnError(new Action1<Throwable>() {

                            /**
                             * This handles failed completions
                             */
                            @Override
                            public void call(Throwable e) {
                                // handle Throwable in case anything is thrown so we don't block Observers waiting for onError/onCompleted
                                Exception ee;
                                if (e instanceof Exception) {
                                    ee = (Exception) e;
                                } else {
                                    ee = new RuntimeException("Throwable caught while executing batch and mapping responses.", e);
                                }
                                logger.debug("Exception mapping responses to requests.", e);
                                // if a failure occurs we want to pass that exception to all of the Futures that we've returned
                                for (CollapsedRequest<ResponseType, RequestArgumentType> request : argumentMap.values()) {
                                    try {
                                        ((CollapsedRequestSubject<ResponseType, RequestArgumentType>) request).setExceptionIfResponseNotReceived(ee);
                                    } catch (IllegalStateException e2) {
                                        // if we have partial responses set in mapResponseToRequests
                                        // then we may get IllegalStateException as we loop over them
                                        // so we'll log but continue to the rest
                                        logger.error("Partial success of 'mapResponseToRequests' resulted in IllegalStateException while setting Exception. Continuing ... ", e2);
                                    }
                                }
                            }

                        }).doOnCompleted(new Action0() {

                            /**
                             * This handles successful completions
                             */
                            @Override
                            public void call() {
                                // check that all requests had setResponse or setException invoked in case 'mapResponseToRequests' was implemented poorly
                                Exception e = null;
                                for (CollapsedRequest<ResponseType, RequestArgumentType> request : shardRequests) {
                                    try {
                                       e = ((CollapsedRequestSubject<ResponseType, RequestArgumentType>) request).setExceptionIfResponseNotReceived(e,"No response set by " + commandCollapser.getCollapserKey().name() + " 'mapResponseToRequests' implementation.");
                                    } catch (IllegalStateException e2) {
                                        logger.debug("Partial success of 'mapResponseToRequests' resulted in IllegalStateException while setting 'No response set' Exception. Continuing ... ", e2);
                                    }
                                }
                            }

                        }).subscribe();
                        
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
                for (CollapsedRequest<ResponseType, RequestArgumentType> request : argumentMap.values()) {
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
                if (argumentMap.size() > 0) {
                    logger.warn("Requests still exist in queue but will not be executed due to RequestCollapser shutdown: " + argumentMap.size(), new IllegalStateException());
                    /*
                     * In the event that there is a concurrency bug or thread scheduling prevents the timer from ticking we need to handle this so the Future.get() calls do not block.
                     * 
                     * I haven't been able to reproduce this use case on-demand but when stressing a machine saw this occur briefly right after the JVM paused (logs stopped scrolling).
                     * 
                     * This safety-net just prevents the CollapsedRequestFutureImpl.get() from waiting on the CountDownLatch until its max timeout.
                     */
                    for (CollapsedRequest<ResponseType, RequestArgumentType> request : argumentMap.values()) {
                        try {
                            ((CollapsedRequestSubject<ResponseType, RequestArgumentType>) request).setExceptionIfResponseNotReceived(new IllegalStateException("Requests not executed before shutdown."));
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

    public int getSize() {
        return argumentMap.size();
    }
}
