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

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import rx.Observable;
import rx.schedulers.Schedulers;
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
 * Given this, the simplest thing to do is just multiplex all single-threaded streams into a global stream.  We can
 * always recover any interesting slice of this stream later (like grouping by {@link HystrixCommandKey}.
 *
 * Also note that any observers of this stream do so on an RxComputation thread.  This allows all processing of
 * events to happen off the main thread executing the {@link HystrixCommand}.  It also implies that event consumers
 * should not expect synchronous invocation.  Each HystrixThreadEventStream will use a dedicated RxComputation thread
 * for the duration of the application's uptime.
 */
public class HystrixThreadEventStream {
    private final long threadId;
    private final String threadName;
    private final Subject<HystrixCommandExecution, HystrixCommandExecution> subject;

    private static final ThreadLocal<HystrixThreadEventStream> threadLocalStreams = new ThreadLocal<HystrixThreadEventStream>() {
        @Override
        protected HystrixThreadEventStream initialValue() {
            HystrixThreadEventStream newThreadEventStream = new HystrixThreadEventStream(Thread.currentThread());
            HystrixGlobalEventStream.registerThreadStream(newThreadEventStream);
            return newThreadEventStream;
        }
    };

    /* package */ HystrixThreadEventStream(Thread thread) {
        this.threadId = thread.getId();
        this.threadName = thread.getName();
        subject = PublishSubject.create();
    }

    public static HystrixThreadEventStream getInstance() {
        return threadLocalStreams.get();
    }

    public void write(HystrixInvokableInfo<?> commandInstance, long[] eventTypeCounts, long executionLatency, long totalLatency) {
        HystrixCommandExecution event = HystrixCommandExecution.from(commandInstance, commandInstance.getCommandKey(), eventTypeCounts, HystrixRequestContext.getContextForCurrentThread(), executionLatency, totalLatency);
        subject.onNext(event);
    }

    public Observable<HystrixCommandExecution> observe() {
        return subject.onBackpressureBuffer().observeOn(Schedulers.computation());
    }

    public void shutdown() {
        subject.onCompleted();
    }

    @Override
    public String toString() {
        return "HystrixThreadEventStream (" + threadId + " - " + threadName + ")";
    }

    private static String bucketToString(long[] bucket) {
        StringBuffer bucketStr = new StringBuffer();
        bucketStr.append("BUCKET(");
        for (HystrixEventType eventType: HystrixEventType.values()) {
            if (bucket[eventType.ordinal()] > 0) {
                bucketStr.append(eventType.name()).append(" -> ").append(bucket[eventType.ordinal()]).append(", ");
            }
        }
        bucketStr.append(")");
        return bucketStr.toString();
    }
}
