package com.netflix.hystrix.contrib.javanica.test.spring.rest.client;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.domain.User;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.resource.UserResource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component("simpleRestClient")
public class SimpleRestClient implements RestClient {

    private static final User DEF_USER = new User("def", "def");

    private UserResource userResource = new UserResource();

    @HystrixCommand(fallbackMethod = "getUserByIdSecondary")
    @Override
    public User getUserById(String id) {
        return userResource.getUserById(id);
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
