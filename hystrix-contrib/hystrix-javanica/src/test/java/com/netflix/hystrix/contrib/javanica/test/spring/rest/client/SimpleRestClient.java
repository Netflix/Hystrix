package com.netflix.hystrix.contrib.javanica.test.spring.rest.client;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.collapser.CollapserResult;
import com.netflix.hystrix.contrib.javanica.command.AsyncCommand;
import com.netflix.hystrix.contrib.javanica.command.ObservableCommand;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.domain.User;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.resource.UserResource;
import org.springframework.stereotype.Component;
import rx.Observable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

@Component("simpleRestClient")
public class SimpleRestClient implements RestClient {

    private static final User DEF_USER = new User("def", "def");

    private UserResource userResource = new UserResource();

    @HystrixCommand(commandKey = "GetUserByIdCommand", fallbackMethod = "getUserByIdSecondary")
    @Override
    public User getUserById(String id) {
        return userResource.getUserById(id);
    }

    @HystrixCommand(fallbackMethod = "getUserByIdSecondary")
    @Override
    public Future<User> getUserByIdAsync(final String id) {
        return new AsyncCommand<User>() {
            @Override
            public User invoke() {
                return userResource.getUserById(id);
            }
        };
    }

    @HystrixCommand(fallbackMethod = "getUserByIdSecondary")
    @Override
    public Observable<User> getUserByIdObservable(final String id) {
        return new ObservableCommand<User>() {
            @Override
            public User invoke() {
                return userResource.getUserById(id);
            }
        };
    }

    @HystrixCommand(fallbackMethod = "defaultUser")
    @Override
    public User getUserByIdSecondary(String id) {
        return userResource.getUserById(id);
    }

    @HystrixCommand(fallbackMethod = "findAllFallback")
    @Override
    public List<User> findAll(int pageNum, int pageSize) {
        return userResource.findAll(pageNum, pageSize);
    }

    @HystrixCommand(fallbackMethod = "findAllFallback")
    @Override
    public Future<List<User>> findAllAsync(final int pageNum, final int pageSize) {
        return new AsyncCommand<List<User>>() {
            @Override
            public List<User> invoke() {
                return userResource.findAll(pageNum, pageSize);
            }
        };
    }

    @HystrixCollapser(commandMethod = "getUserById",
            collapserProperties = {@HystrixProperty(name = "timerDelayInMilliseconds", value = "200")})
    @Override
    public Future<User> getUserByIdCollapserAsync(String id) {
        return CollapserResult.async();
    }

    @HystrixCollapser(commandMethod = "getUserById",
            collapserProperties = {@HystrixProperty(name = "maxRequestsInBatch", value = "3")})
    @Override
    public User getUserByIdCollapser(String id) {
        return CollapserResult.sync();
    }

    @HystrixCommand(fallbackMethod = "findAllFallback2")
    private List<User> findAllFallback(int pageNum, int pageSize) {
        throw new RuntimeException();
    }

    @HystrixCommand
    private List<User> findAllFallback2(int pageNum, int pageSize) {
        return Arrays.asList(DEF_USER);
    }

    @HystrixCommand(commandKey = "GetUserByNameCommand", fallbackMethod = "defaultUser",
            threadPoolKey = "SimpleRestClientTest",
            commandProperties = {
                    @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "500"),
                    @HystrixProperty(name = "execution.isolation.strategy", value = "SEMAPHORE")
            })
    @Override
    public User getUserByName(String name) {
        return userResource.getUserByName(name);
    }

    private User defaultUser(String id) {
        return DEF_USER;
    }

}
