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
package com.netflix.hystrix;

import org.junit.Before;

import com.netflix.hystrix.HystrixCommand.Setter;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func0;
import rx.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class HystrixTest {
    @Before
    public void reset() {
        Hystrix.reset();
    }

    @Test
    public void testNotInThread() {
        assertNull(Hystrix.getCurrentThreadExecutingCommand());
    }

    @Test
      public void testInsideHystrixThreadViaExecute() {

        assertNull(Hystrix.getCurrentThreadExecutingCommand());

        HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("CommandName"))) {

            @Override
            protected Boolean run() {
                assertEquals("CommandName", Hystrix.getCurrentThreadExecutingCommand().name());
                assertEquals(1, Hystrix.getCommandCount());
                return Hystrix.getCurrentThreadExecutingCommand() != null;
            }

        };

        assertTrue(command.execute());
        assertNull(Hystrix.getCurrentThreadExecutingCommand());
        assertEquals(0, Hystrix.getCommandCount());
    }

    @Test
    public void testInsideHystrixThreadViaObserve() {

        assertNull(Hystrix.getCurrentThreadExecutingCommand());

        HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("CommandName"))) {

            @Override
            protected Boolean run() {
                try {
                    //give the caller thread a chance to check that no thread locals are set on it
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    return false;
                }
                assertEquals("CommandName", Hystrix.getCurrentThreadExecutingCommand().name());
                assertEquals(1, Hystrix.getCommandCount());
                return Hystrix.getCurrentThreadExecutingCommand() != null;
            }

        };

        final CountDownLatch latch = new CountDownLatch(1);

        command.observe().subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
                latch.countDown();
            }

            @Override
            public void onNext(Boolean value) {
                System.out.println("OnNext : " + value);
                assertTrue(value);
                assertEquals("CommandName", Hystrix.getCurrentThreadExecutingCommand().name());
                assertEquals(1, Hystrix.getCommandCount());
            }
        });

        try {
            assertNull(Hystrix.getCurrentThreadExecutingCommand());
            assertEquals(0, Hystrix.getCommandCount());
            latch.await();
        } catch (InterruptedException ex) {
            fail(ex.getMessage());
        }

        assertNull(Hystrix.getCurrentThreadExecutingCommand());
        assertEquals(0, Hystrix.getCommandCount());
    }

    @Test
    public void testInsideNestedHystrixThread() {

        HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("OuterCommand"))) {

            @Override
            protected Boolean run() {

                assertEquals("OuterCommand", Hystrix.getCurrentThreadExecutingCommand().name());
                System.out.println("Outer Thread : " + Thread.currentThread().getName());
                //should be a single execution on this thread
                assertEquals(1, Hystrix.getCommandCount());

                if (Hystrix.getCurrentThreadExecutingCommand() == null) {
                    throw new RuntimeException("BEFORE expected it to run inside a thread");
                }

                HystrixCommand<Boolean> command2 = new HystrixCommand<Boolean>(Setter
                        .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                        .andCommandKey(HystrixCommandKey.Factory.asKey("InnerCommand"))) {

                    @Override
                    protected Boolean run() {
                        assertEquals("InnerCommand", Hystrix.getCurrentThreadExecutingCommand().name());
                        System.out.println("Inner Thread : " + Thread.currentThread().getName());
                        //should be a single execution on this thread, since outer/inner are on different threads
                        assertEquals(1, Hystrix.getCommandCount());
                        return Hystrix.getCurrentThreadExecutingCommand() != null;
                    }

                };

                if (Hystrix.getCurrentThreadExecutingCommand() == null) {
                    throw new RuntimeException("AFTER expected it to run inside a thread");
                }

                return command2.execute();
            }

        };

        assertTrue(command.execute());
        assertNull(Hystrix.getCurrentThreadExecutingCommand());
    }

    @Test
    public void testInsideHystrixSemaphoreExecute() {

        HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("SemaphoreIsolatedCommandName"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE))) {

            @Override
            protected Boolean run() {
                assertEquals("SemaphoreIsolatedCommandName", Hystrix.getCurrentThreadExecutingCommand().name());
                System.out.println("Semaphore Thread : " + Thread.currentThread().getName());
                //should be a single execution on the caller thread (since it's busy here)
                assertEquals(1, Hystrix.getCommandCount());
                return Hystrix.getCurrentThreadExecutingCommand() != null;
            }

        };

        // it should be true for semaphore isolation as well
        assertTrue(command.execute());
        // and then be null again once done
        assertNull(Hystrix.getCurrentThreadExecutingCommand());
    }

    @Test
    public void testInsideHystrixSemaphoreQueue() throws Exception {

        HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("SemaphoreIsolatedCommandName"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE))) {

            @Override
            protected Boolean run() {
                assertEquals("SemaphoreIsolatedCommandName", Hystrix.getCurrentThreadExecutingCommand().name());
                System.out.println("Semaphore Thread : " + Thread.currentThread().getName());
                //should be a single execution on the caller thread (since it's busy here)
                assertEquals(1, Hystrix.getCommandCount());
                return Hystrix.getCurrentThreadExecutingCommand() != null;
            }

        };

        // it should be true for semaphore isolation as well
        assertTrue(command.queue().get());
        // and then be null again once done
        assertNull(Hystrix.getCurrentThreadExecutingCommand());
    }

    @Test
    public void testInsideHystrixSemaphoreObserve() throws Exception {

        HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("SemaphoreIsolatedCommandName"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE))) {

            @Override
            protected Boolean run() {
                assertEquals("SemaphoreIsolatedCommandName", Hystrix.getCurrentThreadExecutingCommand().name());
                System.out.println("Semaphore Thread : " + Thread.currentThread().getName());
                //should be a single execution on the caller thread (since it's busy here)
                assertEquals(1, Hystrix.getCommandCount());
                return Hystrix.getCurrentThreadExecutingCommand() != null;
            }

        };

        // it should be true for semaphore isolation as well
        assertTrue(command.toObservable().toBlocking().single());
        // and then be null again once done
        assertNull(Hystrix.getCurrentThreadExecutingCommand());
    }

    @Test
    public void testThreadNestedInsideHystrixSemaphore() {

        HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("OuterSemaphoreCommand"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE))) {

            @Override
            protected Boolean run() {

                assertEquals("OuterSemaphoreCommand", Hystrix.getCurrentThreadExecutingCommand().name());
                System.out.println("Outer Semaphore Thread : " + Thread.currentThread().getName());
                //should be a single execution on the caller thread
                assertEquals(1, Hystrix.getCommandCount());
                if (Hystrix.getCurrentThreadExecutingCommand() == null) {
                    throw new RuntimeException("BEFORE expected it to run inside a semaphore");
                }

                HystrixCommand<Boolean> command2 = new HystrixCommand<Boolean>(Setter
                        .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                        .andCommandKey(HystrixCommandKey.Factory.asKey("InnerCommand"))) {

                    @Override
                    protected Boolean run() {
                        assertEquals("InnerCommand", Hystrix.getCurrentThreadExecutingCommand().name());
                        System.out.println("Inner Thread : " + Thread.currentThread().getName());
                        //should be a single execution on the thread isolating the second command
                        assertEquals(1, Hystrix.getCommandCount());
                        return Hystrix.getCurrentThreadExecutingCommand() != null;
                    }

                };

                if (Hystrix.getCurrentThreadExecutingCommand() == null) {
                    throw new RuntimeException("AFTER expected it to run inside a semaphore");
                }

                return command2.execute();
            }

        };

        assertTrue(command.execute());

        assertNull(Hystrix.getCurrentThreadExecutingCommand());
    }

    @Test
    public void testSemaphoreIsolatedSynchronousHystrixObservableCommand() {
        HystrixObservableCommand<Integer> observableCmd = new SynchronousObservableCommand();

        assertNull(Hystrix.getCurrentThreadExecutingCommand());

        final CountDownLatch latch = new CountDownLatch(1);

        observableCmd.observe().subscribe(new Subscriber<Integer>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
                latch.countDown();
            }

            @Override
            public void onNext(Integer value) {
                System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " SyncObservable latched Subscriber OnNext : " + value);
            }
        });

        try {
            assertNull(Hystrix.getCurrentThreadExecutingCommand());
            assertEquals(0, Hystrix.getCommandCount());
            latch.await();
        } catch (InterruptedException ex) {
            fail(ex.getMessage());
        }

        assertNull(Hystrix.getCurrentThreadExecutingCommand());
        assertEquals(0, Hystrix.getCommandCount());
    }

//    @Test
//    public void testSemaphoreIsolatedAsynchronousHystrixObservableCommand() {
//        HystrixObservableCommand<Integer> observableCmd = new AsynchronousObservableCommand();
//
//        assertNull(Hystrix.getCurrentThreadExecutingCommand());
//
//        final CountDownLatch latch = new CountDownLatch(1);
//
//        observableCmd.observe().subscribe(new Subscriber<Integer>() {
//            @Override
//            public void onCompleted() {
//                latch.countDown();
//            }
//
//            @Override
//            public void onError(Throwable e) {
//                fail(e.getMessage());
//                latch.countDown();
//            }
//
//            @Override
//            public void onNext(Integer value) {
//                System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " AsyncObservable latched Subscriber OnNext : " + value);
//            }
//        });
//
//        try {
//            assertNull(Hystrix.getCurrentThreadExecutingCommand());
//            assertEquals(0, Hystrix.getCommandCount());
//            latch.await();
//        } catch (InterruptedException ex) {
//            fail(ex.getMessage());
//        }
//
//        assertNull(Hystrix.getCurrentThreadExecutingCommand());
//        assertEquals(0, Hystrix.getCommandCount());
//    }

    @Test
    public void testMultipleSemaphoreObservableCommandsInFlight() throws InterruptedException {
        int NUM_COMMANDS = 50;
        List<Observable<Integer>> commands = new ArrayList<Observable<Integer>>();
        for (int i = 0; i < NUM_COMMANDS; i++) {
            commands.add(Observable.defer(new Func0<Observable<Integer>>() {
                @Override
                public Observable<Integer> call() {
                    return new AsynchronousObservableCommand().observe();
                }
            }));
        }

        final AtomicBoolean exceptionFound = new AtomicBoolean(false);

        final CountDownLatch latch = new CountDownLatch(1);

        Observable.merge(commands).subscribe(new Subscriber<Integer>() {
            @Override
            public void onCompleted() {
                System.out.println("OnCompleted");
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("OnError : " + e);
                e.printStackTrace();
                exceptionFound.set(true);
                latch.countDown();
            }

            @Override
            public void onNext(Integer n) {
                System.out.println("OnNext : " + n + " : " + Thread.currentThread().getName() + " : " + Hystrix.getCommandCount());// + " : " + Hystrix.getCurrentThreadExecutingCommand().name() + " : " + Hystrix.getCommandCount());
            }
        });

        latch.await();

        assertFalse(exceptionFound.get());
    }

    //see https://github.com/Netflix/Hystrix/issues/280
    @Test
    public void testResetCommandProperties() {
        HystrixCommand<Boolean> cmd1 = new ResettableCommand(100, 1, 10);
        assertEquals(100L, (long) cmd1.getProperties().executionTimeoutInMilliseconds().get());
        assertEquals(1L, (long) cmd1.getProperties().executionIsolationSemaphoreMaxConcurrentRequests().get());
        //assertEquals(10L, (long) cmd1.threadPool.getExecutor()..getCorePoolSize());

        Hystrix.reset();

        HystrixCommand<Boolean> cmd2 = new ResettableCommand(700, 2, 40);
        assertEquals(700L, (long) cmd2.getProperties().executionTimeoutInMilliseconds().get());
        assertEquals(2L, (long) cmd2.getProperties().executionIsolationSemaphoreMaxConcurrentRequests().get());
        //assertEquals(40L, (long) cmd2.threadPool.getExecutor().getCorePoolSize());
	}

    private static class SynchronousObservableCommand extends HystrixObservableCommand<Integer> {

        protected SynchronousObservableCommand() {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("GROUP"))
                            .andCommandKey(HystrixCommandKey.Factory.asKey("SyncObservable"))
                            .andCommandPropertiesDefaults(new HystrixCommandProperties.Setter().withExecutionIsolationSemaphoreMaxConcurrentRequests(1000))
            );
        }

        @Override
        protected Observable<Integer> construct() {
            return Observable.create(new Observable.OnSubscribe<Integer>() {
                @Override
                public void call(Subscriber<? super Integer> subscriber) {
                    try {
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " SyncCommand construct()");
                        assertEquals("SyncObservable", Hystrix.getCurrentThreadExecutingCommand().name());
                        assertEquals(1, Hystrix.getCommandCount());
                        Thread.sleep(10);
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " SyncCommand construct() -> OnNext(1)");
                        subscriber.onNext(1);
                        Thread.sleep(10);
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " SyncCommand construct() -> OnNext(2)");
                        subscriber.onNext(2);
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " SyncCommand construct() -> OnCompleted");
                        subscriber.onCompleted();
                    } catch (Throwable ex) {
                        subscriber.onError(ex);
                    }
                }
            });
        }
    }

    private static class AsynchronousObservableCommand extends HystrixObservableCommand<Integer> {

        protected AsynchronousObservableCommand() {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("GROUP"))
                            .andCommandKey(HystrixCommandKey.Factory.asKey("AsyncObservable"))
                            .andCommandPropertiesDefaults(new HystrixCommandProperties.Setter().withExecutionIsolationSemaphoreMaxConcurrentRequests(1000))
            );
        }

        @Override
        protected Observable<Integer> construct() {
            return Observable.create(new Observable.OnSubscribe<Integer>() {
                @Override
                public void call(Subscriber<? super Integer> subscriber) {
                    try {
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " AsyncCommand construct()");
                        Thread.sleep(10);
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " AsyncCommand construct() -> OnNext(1)");
                        subscriber.onNext(1);
                        Thread.sleep(10);
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " AsyncCommand construct() -> OnNext(2)");
                        subscriber.onNext(2);
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " AsyncCommand construct() -> OnCompleted");
                        subscriber.onCompleted();
                    } catch (Throwable ex) {
                        subscriber.onError(ex);
                    }
                }
            }).subscribeOn(Schedulers.computation());
        }
    }

    private static class ResettableCommand extends HystrixCommand<Boolean> {
        ResettableCommand(int timeout, int semaphoreCount, int poolCoreSize) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("GROUP"))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                            .withExecutionTimeoutInMilliseconds(timeout)
                    .withExecutionIsolationSemaphoreMaxConcurrentRequests(semaphoreCount))
                    .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter().withCoreSize(poolCoreSize)));
        }

        @Override
        protected Boolean run() throws Exception {
            return true;
        }
    }
}
