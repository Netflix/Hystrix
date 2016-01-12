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
package com.netflix.hystrix.metric;

import com.netflix.hystrix.ExecutionResult;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.HystrixThreadPoolKey;
import rx.functions.Action1;
import rx.observers.Subscribers;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

/**
 * Per-thread event stream.  No synchronization required when writing to it since it's single-threaded.
 *
 * Some threads will be dedicated to a single HystrixCommandKey (a member of a thread-isolated {@link HystrixThreadPool}.
 * However, many situations arise where a single thread may serve many different commands.  Examples include:
 * * Application caller threads (semaphore-isolated commands, or thread-pool-rejections)
 * * Timer threads (timeouts or collapsers)
 *
 * This is not a useful stream to consume directly (I think), so this stream writes to the proper {@link HystrixCommandCompletionStream} and
 * {@link HystrixThreadPoolCompletionStream} (when executed in / rejected from a thread pool)
 *
 * Also note that any observers of this stream do so on an RxComputation thread.  This allows all processing of
 * events to happen off the main thread executing the {@link HystrixCommand}.  It also implies that event consumers
 * should not expect synchronous invocation.  Each HystrixThreadEventStream will use a dedicated RxComputation thread
 * for the duration of the application's uptime.
 */
public class HystrixThreadEventStream {
    private final long threadId;
    private final String threadName;

    private final Subject<HystrixCommandExecutionStarted, HystrixCommandExecutionStarted> writeOnlyCommandStartSubject;
    private final Subject<HystrixCommandCompletion, HystrixCommandCompletion> writeOnlyCommandCompletionSubject;
    private final Subject<HystrixCollapserEvent, HystrixCollapserEvent> writeOnlyCollapserSubject;

    private static final ThreadLocal<HystrixThreadEventStream> threadLocalStreams = new ThreadLocal<HystrixThreadEventStream>() {
        @Override
        protected HystrixThreadEventStream initialValue() {
            return new HystrixThreadEventStream(Thread.currentThread());
        }
    };

    private static final Action1<HystrixCommandExecutionStarted> writeCommandStartsToShardedStreams = new Action1<HystrixCommandExecutionStarted>() {
        @Override
        public void call(HystrixCommandExecutionStarted event) {
            HystrixCommandStartStream commandStartStream = HystrixCommandStartStream.getInstance(event.getCommandKey());
            commandStartStream.write(event);

            if (event.isExecutedInThread()) {
                HystrixThreadPoolStartStream threadPoolStartStream = HystrixThreadPoolStartStream.getInstance(event.getThreadPoolKey());
                threadPoolStartStream.write(event);
            }
        }
    };

    private static final Action1<HystrixCommandCompletion> writeCommandCompletionsToShardedStreams = new Action1<HystrixCommandCompletion>() {
        @Override
        public void call(HystrixCommandCompletion commandCompletion) {
            HystrixCommandCompletionStream commandStream = HystrixCommandCompletionStream.getInstance(commandCompletion.getCommandKey());
            commandStream.write(commandCompletion);

            if (commandCompletion.isExecutedInThread() || commandCompletion.isResponseThreadPoolRejected()) {
                HystrixThreadPoolCompletionStream threadPoolStream = HystrixThreadPoolCompletionStream.getInstance(commandCompletion.getThreadPoolKey());
                threadPoolStream.write(commandCompletion);
            }
        }
    };

    private static final Action1<HystrixCollapserEvent> writeCollapserExecutionsToShardedStreams = new Action1<HystrixCollapserEvent>() {
        @Override
        public void call(HystrixCollapserEvent collapserEvent) {
            HystrixCollapserEventStream collapserStream = HystrixCollapserEventStream.getInstance(collapserEvent.getCollapserKey());
            collapserStream.write(collapserEvent);
        }
    };

    /* package */ HystrixThreadEventStream(Thread thread) {
        this.threadId = thread.getId();
        this.threadName = thread.getName();
        writeOnlyCommandStartSubject = PublishSubject.create();
        writeOnlyCommandCompletionSubject = PublishSubject.create();
        writeOnlyCollapserSubject = PublishSubject.create();

        writeOnlyCommandStartSubject
                .onBackpressureBuffer()
                //.observeOn(Schedulers.computation())
                .doOnNext(writeCommandStartsToShardedStreams)
                .unsafeSubscribe(Subscribers.empty());

        writeOnlyCommandCompletionSubject
                .onBackpressureBuffer()
                //.observeOn(Schedulers.computation())
                .doOnNext(writeCommandCompletionsToShardedStreams)
                .unsafeSubscribe(Subscribers.empty());

        writeOnlyCollapserSubject
                .onBackpressureBuffer()
                //.observeOn(Schedulers.computation())
                .doOnNext(writeCollapserExecutionsToShardedStreams)
                .unsafeSubscribe(Subscribers.empty());
    }

    public static HystrixThreadEventStream getInstance() {
        return threadLocalStreams.get();
    }

    public void shutdown() {
        writeOnlyCommandStartSubject.onCompleted();
        writeOnlyCommandCompletionSubject.onCompleted();
        writeOnlyCollapserSubject.onCompleted();
    }

    public void commandExecutionStarted(HystrixCommandKey commandKey, HystrixThreadPoolKey threadPoolKey,
                                        HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy, int currentConcurrency) {
        HystrixCommandExecutionStarted event = new HystrixCommandExecutionStarted(commandKey, threadPoolKey, isolationStrategy, currentConcurrency);
        writeOnlyCommandStartSubject.onNext(event);
    }

    public void executionDone(ExecutionResult executionResult, HystrixCommandKey commandKey, HystrixThreadPoolKey threadPoolKey) {
        HystrixCommandCompletion event = HystrixCommandCompletion.from(executionResult, commandKey, threadPoolKey);
        writeOnlyCommandCompletionSubject.onNext(event);
    }

    public void collapserResponseFromCache(HystrixCollapserKey collapserKey) {
        HystrixCollapserEvent collapserEvent = HystrixCollapserEvent.from(collapserKey, HystrixEventType.Collapser.RESPONSE_FROM_CACHE, 1);
        writeOnlyCollapserSubject.onNext(collapserEvent);
    }

    public void collapserBatchExecuted(HystrixCollapserKey collapserKey, int batchSize) {
        HystrixCollapserEvent batchExecution = HystrixCollapserEvent.from(collapserKey, HystrixEventType.Collapser.BATCH_EXECUTED, 1);
        HystrixCollapserEvent batchAdditions = HystrixCollapserEvent.from(collapserKey, HystrixEventType.Collapser.ADDED_TO_BATCH, batchSize);
        writeOnlyCollapserSubject.onNext(batchExecution);
        writeOnlyCollapserSubject.onNext(batchAdditions);
    }

    @Override
    public String toString() {
        return "HystrixThreadEventStream (" + threadId + " - " + threadName + ")";
    }
}
