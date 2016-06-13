/**
 * Copyright 2016 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.hystrix.contrib.reactivesocket;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixObservableCommand;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.util.Random;

public class HystrixStreamTest {

    static final Random r = new Random();

    protected static class SyntheticBlockingCommand extends HystrixCommand<Integer> {

        public SyntheticBlockingCommand() {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("UNITTEST")));
        }

        @Override
        protected Integer run() throws Exception {
            int n = r.nextInt(100);
            Thread.sleep(n);
            return n;
        }
    }

    protected static class SyntheticAsyncCommand extends HystrixObservableCommand<Integer> {

        public SyntheticAsyncCommand() {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("UNITTEST")));
        }

        @Override
        protected Observable<Integer> construct() {
            return Observable.defer(() -> {
                try {
                    int n = r.nextInt(100);
                    Thread.sleep(n);
                    return Observable.just(n);
                } catch (InterruptedException ex) {
                    return Observable.error(ex);
                }
            }).subscribeOn(Schedulers.io());
        }
    }
}
