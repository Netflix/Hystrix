package com.netflix.hystrix.contrib.javanica.test.spring.rest.resource;


import static org.slf4j.helpers.MessageFormatter.format;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.domain.User;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.exception.BadRequestException;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.exception.NotFoundException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

public class UserResource {

    public static final Map<String, User> USER_MAP = ImmutableMap.<String, User>builder()
        .put("1", new User("1", "Alex"))
        .put("2", new User("2", "Dave"))
        .put("3", new User("3", "Jeff"))
        .put("4", new User("4", "Chris")).build();
    private static final String USER_ID_ERROR_MSG = "user id cannot be null or empty. current value: {}";
    private static final String USER_NOT_FOUND = "user with id {} not found";

    public User getUserById(String id) {
        if (StringUtils.isBlank(id)) {
            throw new BadRequestException(format(USER_ID_ERROR_MSG, id).getMessage());
        }
        User user = USER_MAP.get(id);
        if (user == null) {
            throw new NotFoundException(format(USER_NOT_FOUND, id).getMessage());
        }
        return user;
    }

    public User getUserByName(String name) {
        User user = null;

        if ("timeout".equalsIgnoreCase(name)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                //e.printStackTrace();
            }
        }
        if (StringUtils.isBlank(name)) {
            throw new BadRequestException("user name cannot be null or empty");
        }
        for (Map.Entry<String, User> entry : USER_MAP.entrySet()) {
            if (name.equalsIgnoreCase(entry.getValue().getName())) {
                user = entry.getValue();
                break;
            }
        }
        if (user == null) {
            throw new NotFoundException("user with name '" + name + "' not found");
        }
        return user;
    }

    public List<User> findAll(int pageNum, int pageSize) {
        List<User> users = Lists.newArrayList(USER_MAP.values())
            .subList(pageNum - 1 * pageSize, pageNum * pageSize);
        if (CollectionUtils.isEmpty(users)) {
            throw new NotFoundException();
        }
        return users;
    }

}
