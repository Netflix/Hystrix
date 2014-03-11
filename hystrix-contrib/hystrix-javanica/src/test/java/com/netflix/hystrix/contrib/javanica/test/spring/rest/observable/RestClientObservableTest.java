package com.netflix.hystrix.contrib.javanica.test.spring.rest.observable;


import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.client.RestClient;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.domain.User;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import rx.Observable;
import rx.Observer;
import rx.util.functions.Action1;

import static com.netflix.hystrix.contrib.javanica.CommonUtils.getHystrixCommandByKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = AopCglibConfig.class)
public class RestClientObservableTest {
    @Autowired
    protected RestClient restClient;

    private HystrixRequestContext context;

    @Before
    public void setUp() throws Exception {
        context = HystrixRequestContext.initializeContext();
    }

    @After
    public void tearDown() throws Exception {
        context.shutdown();
    }

    @Test
    public void testGetUserByIdObservable() {
        final User exUser = new User("1", "Alex");

        // blocking
        assertEquals(exUser, restClient.getUserByIdObservable("1").toBlockingObservable().single());

        // non-blocking
        // - this is a verbose anonymous inner-class approach and doesn't do assertions
        Observable<User> fUser = restClient.getUserByIdObservable("1");
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

        Observable<User> fs = restClient.getUserByIdObservable("1");
        fs.subscribe(new Action1<User>() {

            @Override
            public void call(User user) {
                assertEquals(exUser, user);
            }
        });
        assertEquals(3, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        HystrixCommand getUserByIdObservable = getHystrixCommandByKey("getUserByIdObservable");
        assertTrue(getUserByIdObservable.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void testGetUserByIdObservableWithFallback() {
        final User exUser = new User("def", "def");

        // blocking
        assertEquals(exUser, restClient.getUserByIdObservable("blah").toBlockingObservable().single());
        assertEquals(2, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        HystrixCommand getUserByIdObservable = getHystrixCommandByKey("getUserByIdObservable");
        // confirm that command has failed
        assertTrue(getUserByIdObservable.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that fallback was successful
        assertTrue(getUserByIdObservable.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

}
