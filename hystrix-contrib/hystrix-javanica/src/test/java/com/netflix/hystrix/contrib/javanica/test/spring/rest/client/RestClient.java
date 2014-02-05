package com.netflix.hystrix.contrib.javanica.test.spring.rest.client;


import com.netflix.hystrix.contrib.javanica.test.spring.rest.domain.User;

import java.util.List;


public interface RestClient {

    User getUserById(String id);

    User getUserByName(String name);

    /* for testing*/
    User getUserByIdSecondary(String id);

    List<User> findAll(int pageNum, int pageSize);
}
