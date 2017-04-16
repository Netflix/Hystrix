/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.test.common.observable;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.ObservableExecutionMode;
import com.netflix.hystrix.contrib.javanica.test.common.BasicHystrixTest;
import com.netflix.hystrix.contrib.javanica.test.common.domain.User;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import rx.Completable;
import rx.Observable;
import rx.Observer;
import rx.Single;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func0;

import static com.netflix.hystrix.contrib.javanica.test.common.CommonUtils.getHystrixCommandByKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by dmgcodevil
 */
public abstract class BasicObservableTest extends BasicHystrixTest {


    private UserService userService;

    protected abstract UserService createUserService();

    @Before
    public void setUp() throws Exception {
        userService = createUserService();
    }

    @Test
    public void testGetUserByIdSuccess() {
        // blocking
        Observable<User> observable = userService.getUser("1", "name: ");
        assertEquals("name: 1", observable.toBlocking().single().getName());

        // non-blocking
        // - this is a verbose anonymous inner-class approach and doesn't do assertions
        Observable<User> fUser = userService.getUser("1", "name: ");
        fUser.subscribe(new Observer<User>() {

            @Override
            public void onCompleted() {
                // nothing needed here
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onNext(User v) {
                System.out.println("onNext: " + v);
            }

        });

        Observable<User> fs = userService.getUser("1", "name: ");
        fs.subscribe(new Action1<User>() {

            @Override
            public void call(User user) {
                assertEquals("name: 1", user.getName());
            }
        });
        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        com.netflix.hystrix.HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getUser");
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void testGetCompletableUser(){
        userService.getCompletableUser("1", "name: ");
        com.netflix.hystrix.HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getCompletableUser");
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void testGetCompletableUserWithRegularFallback() {
        Completable completable = userService.getCompletableUserWithRegularFallback(null, "name: ");
        completable.<User>toObservable().subscribe(new Action1<User>() {
            @Override
            public void call(User user) {
                assertEquals("default_id", user.getId());
            }
        });
        com.netflix.hystrix.HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getCompletableUserWithRegularFallback");
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

    @Test
    public void testGetCompletableUserWithRxFallback() {
        Completable completable = userService.getCompletableUserWithRxFallback(null, "name: ");
        completable.<User>toObservable().subscribe(new Action1<User>() {
            @Override
            public void call(User user) {
                assertEquals("default_id", user.getId());
            }
        });
        com.netflix.hystrix.HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getCompletableUserWithRxFallback");
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

    @Test
    public void testGetSingleUser() {
        final String id = "1";
        Single<User> user = userService.getSingleUser(id, "name: ");
        user.subscribe(new Action1<User>() {
            @Override
            public void call(User user) {
                assertEquals(id, user.getId());
            }
        });
        com.netflix.hystrix.HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getSingleUser");
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void testGetSingleUserWithRegularFallback(){
        Single<User> user = userService.getSingleUserWithRegularFallback(null, "name: ");
        user.subscribe(new Action1<User>() {
            @Override
            public void call(User user) {
                assertEquals("default_id", user.getId());
            }
        });
        com.netflix.hystrix.HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getSingleUserWithRegularFallback");
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

    @Test
    public void testGetSingleUserWithRxFallback(){
        Single<User> user = userService.getSingleUserWithRxFallback(null, "name: ");
        user.subscribe(new Action1<User>() {
            @Override
            public void call(User user) {
                assertEquals("default_id", user.getId());
            }
        });
        com.netflix.hystrix.HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getSingleUserWithRxFallback");
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

    @Test
    public void testGetUserWithRegularFallback() {
        final User exUser = new User("def", "def");
        Observable<User> userObservable = userService.getUserRegularFallback(" ", "");
        // blocking
        assertEquals(exUser, userObservable.toBlocking().single());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        com.netflix.hystrix.HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getUserRegularFallback");
        // confirm that command has failed
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that fallback was successful
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

    @Test
    public void testGetUserWithRxFallback() {
        final User exUser = new User("def", "def");

        // blocking
        assertEquals(exUser, userService.getUserRxFallback(" ", "").toBlocking().single());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        com.netflix.hystrix.HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getUserRxFallback");
        // confirm that command has failed
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that fallback was successful
        assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

    @Test
    public void testGetUserWithRxCommandFallback() {
        final User exUser = new User("def", "def");

        // blocking
        Observable<User> userObservable = userService.getUserRxCommandFallback(" ", "");
        assertEquals(exUser, userObservable.toBlocking().single());
        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        com.netflix.hystrix.HystrixInvokableInfo getUserRxCommandFallback = getHystrixCommandByKey("getUserRxCommandFallback");
        com.netflix.hystrix.HystrixInvokableInfo rxCommandFallback = getHystrixCommandByKey("rxCommandFallback");
        // confirm that command has failed
        assertTrue(getUserRxCommandFallback.getExecutionEvents().contains(HystrixEventType.FAILURE));
        assertTrue(getUserRxCommandFallback.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
        // and that fallback command was successful
        assertTrue(rxCommandFallback.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }


    public static class UserService {

        private User regularFallback(String id, String name) {
            return new User("def", "def");
        }

        private Observable<User> rxFallback(String id, String name) {
            return Observable.just(new User("def", "def"));
        }

        @HystrixCommand(observableExecutionMode = ObservableExecutionMode.EAGER)
        private Observable<User> rxCommandFallback(String id, String name, Throwable throwable) {
            if (throwable instanceof GetUserException && "getUserRxCommandFallback has failed".equals(throwable.getMessage())) {
                return Observable.just(new User("def", "def"));
            } else {
                throw new IllegalStateException();
            }

        }

        @HystrixCommand
        public Observable<User> getUser(final String id, final String name) {
            validate(id, name, "getUser has failed");
            return createObservable(id, name);
        }

        @HystrixCommand
        public Completable getCompletableUser(final String id, final String name) {
            validate(id, name, "getCompletableUser has failed");
            return createObservable(id, name).toCompletable();
        }

        @HystrixCommand(fallbackMethod = "completableUserRegularFallback")
        public Completable getCompletableUserWithRegularFallback(final String id, final String name) {
            return getCompletableUser(id, name);
        }

        @HystrixCommand(fallbackMethod = "completableUserRxFallback")
        public Completable getCompletableUserWithRxFallback(final String id, final String name) {
            return getCompletableUser(id, name);
        }

        public User completableUserRegularFallback(final String id, final String name) {
            return new User("default_id", "default_name");
        }

        public Completable completableUserRxFallback(final String id, final String name) {
            return Completable.fromCallable(new Func0<User>() {
                @Override
                public User call() {
                    return new User("default_id", "default_name");
                }
            });
        }

        @HystrixCommand
        public Single<User> getSingleUser(final String id, final String name) {
            validate(id, name, "getSingleUser has failed");
            return createObservable(id, name).toSingle();
        }

        @HystrixCommand(fallbackMethod = "singleUserRegularFallback")
        public Single<User> getSingleUserWithRegularFallback(final String id, final String name) {
            return getSingleUser(id, name);
        }

        @HystrixCommand(fallbackMethod = "singleUserRxFallback")
        public Single<User> getSingleUserWithRxFallback(final String id, final String name) {
            return getSingleUser(id, name);
        }

        User singleUserRegularFallback(final String id, final String name) {
            return new User("default_id", "default_name");
        }

        Single<User> singleUserRxFallback(final String id, final String name) {
            return createObservable("default_id", "default_name").toSingle();
        }

        @HystrixCommand(fallbackMethod = "regularFallback", observableExecutionMode = ObservableExecutionMode.LAZY)
        public Observable<User> getUserRegularFallback(final String id, final String name) {
            validate(id, name, "getUser has failed");
            return createObservable(id, name);
        }

        @HystrixCommand(fallbackMethod = "rxFallback")
        public Observable<User> getUserRxFallback(final String id, final String name) {
            validate(id, name, "getUserRxFallback has failed");
            return createObservable(id, name);
        }

        @HystrixCommand(fallbackMethod = "rxCommandFallback", observableExecutionMode = ObservableExecutionMode.LAZY)
        public Observable<User> getUserRxCommandFallback(final String id, final String name) {
            validate(id, name, "getUserRxCommandFallback has failed");
            return createObservable(id, name);
        }


        private Observable<User> createObservable(final String id, final String name) {
            return Observable.create(new Observable.OnSubscribe<User>() {
                @Override
                public void call(Subscriber<? super User> observer) {
                    try {
                        if (!observer.isUnsubscribed()) {
                            observer.onNext(new User(id, name + id));
                            observer.onCompleted();
                        }
                    } catch (Exception e) {
                        observer.onError(e);
                    }
                }
            });
        }

        private void validate(String id, String name, String errorMsg) {
            if (StringUtils.isBlank(id) || StringUtils.isBlank(name)) {
                throw new GetUserException(errorMsg);
            }
        }

        private static final class GetUserException extends RuntimeException {
            public GetUserException(String message) {
                super(message);
            }
        }
    }
}
