package com.netflix.hystrix.contrib.javanica.test.spring.rest.client;


import com.netflix.hystrix.contrib.javanica.test.spring.rest.domain.User;
import rx.Observable;

import java.util.List;
import java.util.concurrent.Future;

public interface RestClient {

    User getUserById(String id);

    Future<User> getUserByIdAsync(String id);

    Observable<User> getUserByIdObservable(String id);

    User getUserByName(String name);

    User getUserByNameIgnoreExc(String name);

    User getUserByIdSecondary(String id);

    List<User> findAll(int pageNum, int pageSize);

    Future<List<User>> findAllAsync(int pageNum, int pageSize);

    Future<User> getUserByIdCollapserAsync(String id);

    User getUserByIdCollapser(String id);

}
