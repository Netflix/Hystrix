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
package com.netflix.hystrix.contrib.javanica.test.common.cache;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheKey;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheResult;
import com.netflix.hystrix.contrib.javanica.exception.HystrixCachingException;
import com.netflix.hystrix.contrib.javanica.test.common.BasicHystrixTest;
import com.netflix.hystrix.contrib.javanica.test.common.domain.Profile;
import com.netflix.hystrix.contrib.javanica.test.common.domain.User;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.netflix.hystrix.contrib.javanica.test.common.CommonUtils.getLastExecutedCommand;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by dmgcodevil
 */
public abstract class BasicCacheTest extends BasicHystrixTest {

    private UserService userService;

    @Before
    public void setUp() throws Exception {
        userService = createUserService();
    }

    protected abstract UserService createUserService();

    /**
     * Get-Set-Get with Request Cache Invalidation Test.
     * <p/>
     * given:
     * command to get user by id, see {@link UserService#getUserById(String)}
     * command to update user, see {@link UserService#update(com.netflix.hystrix.contrib.javanica.test.common.domain.User)}
     * <p/>
     * when:
     * 1. call {@link UserService#getUserById(String)}
     * 2. call {@link UserService#getUserById(String)}
     * 3. call {@link UserService#update(com.netflix.hystrix.contrib.javanica.test.common.domain.User)}
     * 4. call {@link UserService#getUserById(String)}
     * <p/>
     * then:
     * at the first time "getUserById" command shouldn't retrieve value from cache
     * at the second time "getUserById" command should retrieve value from cache
     * "update" method should update an user and flush cache related to "getUserById" command
     * after "update" method execution "getUserById" command shouldn't retrieve value from cache
     */
    @Test
    public void testGetSetGetUserCache_givenTwoCommands() {

        User user = userService.getUserById("1");
        HystrixInvokableInfo<?> getUserByIdCommand = getLastExecutedCommand();
        // this is the first time we've executed this command with
        // the value of "1" so it should not be from cache
        assertFalse(getUserByIdCommand.isResponseFromCache());
        assertEquals("1", user.getId());
        assertEquals("name", user.getName()); // initial name value

        user = userService.getUserById("1");
        assertEquals("1", user.getId());
        getUserByIdCommand = getLastExecutedCommand();
        // this is the second time we've executed this command with
        // the same value so it should return from cache
        assertTrue(getUserByIdCommand.isResponseFromCache());
        assertEquals("name", user.getName()); // same name

        // create new user with same id but with new name
        user = new User("1", "new_name");
        userService.update(user); // update the user

        user = userService.getUserById("1");
        getUserByIdCommand = getLastExecutedCommand();
        // this is the first time we've executed this command after "update"
        // method was invoked and a cache for "getUserById" command was flushed
        // so the response should not be from cache
        assertFalse(getUserByIdCommand.isResponseFromCache());
        assertEquals("1", user.getId());
        assertEquals("new_name", user.getName());

        // start a new request context
        resetContext();

        user = userService.getUserById("1");
        getUserByIdCommand = getLastExecutedCommand();
        assertEquals("1", user.getId());
        // this is a new request context so this
        // should not come from cache
        assertFalse(getUserByIdCommand.isResponseFromCache());

    }

    @Test
    public void testGetSetGetUserCache_givenGetUserByEmailAndUpdateProfile() {
        User user = userService.getUserByEmail("email");
        HystrixInvokableInfo<?> getUserByIdCommand = getLastExecutedCommand();
        // this is the first time we've executed this command with
        // the value of "1" so it should not be from cache
        assertFalse(getUserByIdCommand.isResponseFromCache());
        assertEquals("1", user.getId());
        assertEquals("name", user.getName());
        assertEquals("email", user.getProfile().getEmail()); // initial email value

        user = userService.getUserByEmail("email");
        assertEquals("1", user.getId());
        getUserByIdCommand = getLastExecutedCommand();
        // this is the second time we've executed this command with
        // the same value so it should return from cache
        assertTrue(getUserByIdCommand.isResponseFromCache());
        assertEquals("email", user.getProfile().getEmail()); // same email

        // create new user with same id but with new email
        Profile profile = new Profile();
        profile.setEmail("new_email");
        user.setProfile(profile);
        userService.updateProfile(user); // update the user profile

        user = userService.getUserByEmail("new_email");
        getUserByIdCommand = getLastExecutedCommand();
        // this is the first time we've executed this command after "updateProfile"
        // method was invoked and a cache for "getUserByEmail" command was flushed
        // so the response should not be from cache
        assertFalse(getUserByIdCommand.isResponseFromCache());
        assertEquals("1", user.getId());
        assertEquals("name", user.getName());
        assertEquals("new_email", user.getProfile().getEmail());

        // start a new request context
        resetContext();

        user = userService.getUserByEmail("new_email");
        getUserByIdCommand = getLastExecutedCommand();
        assertEquals("1", user.getId());
        // this is a new request context so this
        // should not come from cache
        assertFalse(getUserByIdCommand.isResponseFromCache());
    }

    @Test
    public void testGetSetGetUserCache_givenOneCommandAndOneMethodAnnotatedWithCacheRemove() {
        // given
        User user = userService.getUserById("1");
        HystrixInvokableInfo<?> getUserByIdCommand = getLastExecutedCommand();
        // this is the first time we've executed this command with
        // the value of "1" so it should not be from cache
        assertFalse(getUserByIdCommand.isResponseFromCache());
        assertEquals("1", user.getId());
        assertEquals("name", user.getName()); // initial name value

        user = userService.getUserById("1");
        assertEquals("1", user.getId());
        getUserByIdCommand = getLastExecutedCommand();
        // this is the second time we've executed this command with
        // the same value so it should return from cache
        assertTrue(getUserByIdCommand.isResponseFromCache());
        assertEquals("name", user.getName()); // same name

        // when
        userService.updateName("1", "new_name"); // update the user name

        // then
        user = userService.getUserById("1");
        getUserByIdCommand = getLastExecutedCommand();
        // this is the first time we've executed this command after "update"
        // method was invoked and a cache for "getUserById" command was flushed
        // so the response should not be from cache
        assertFalse(getUserByIdCommand.isResponseFromCache());
        assertEquals("1", user.getId());
        assertEquals("new_name", user.getName());

        // start a new request context
        resetContext();
        user = userService.getUserById("1");
        getUserByIdCommand = getLastExecutedCommand();
        assertEquals("1", user.getId());
        // this is a new request context so this
        // should not come from cache
        assertFalse(getUserByIdCommand.isResponseFromCache());
    }


    @Test(expected = HystrixCachingException.class)
    public void testGetUser_givenWrongCacheKeyMethodReturnType_shouldThrowException() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            User user = userService.getUserByName("name");
        } finally {
            context.shutdown();
        }
    }

    @Test(expected = HystrixCachingException.class)
    public void testGetUserByName_givenNonexistentCacheKeyMethod_shouldThrowException() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            User user = userService.getUser();
        } finally {
            context.shutdown();
        }
    }

    public static class UserService {
        private Map<String, User> storage = new ConcurrentHashMap<String, User>();

        @PostConstruct
        public void init() {
            User user = new User("1", "name");
            Profile profile = new Profile();
            profile.setEmail("email");
            user.setProfile(profile);
            storage.put("1", user);
        }

        @CacheResult
        @HystrixCommand
        public User getUserById(@CacheKey String id) {
            return storage.get(id);
        }

        @CacheResult(cacheKeyMethod = "getUserByNameCacheKey")
        @HystrixCommand
        public User getUserByName(String name) {
            return null;
        }

        private Long getUserByNameCacheKey() {
            return 0L;
        }

        @CacheResult(cacheKeyMethod = "nonexistent")
        @HystrixCommand
        public User getUser() {
            return null;
        }

        @CacheResult(cacheKeyMethod = "getUserByEmailCacheKey")
        @HystrixCommand
        public User getUserByEmail(final String email) {
            return Iterables.tryFind(storage.values(), new Predicate<User>() {
                @Override
                public boolean apply(User input) {
                    return input.getProfile().getEmail().equalsIgnoreCase(email);
                }
            }).orNull();
        }

        private String getUserByEmailCacheKey(String email) {
            return email;
        }

        @CacheRemove(commandKey = "getUserById")
        @HystrixCommand
        public void update(@CacheKey("id") User user) {
            storage.put(user.getId(), user);
        }

        @CacheRemove(commandKey = "getUserByEmail")
        @HystrixCommand
        public void updateProfile(@CacheKey("profile.email") User user) {
            storage.get(user.getId()).setProfile(user.getProfile());
        }

        @CacheRemove(commandKey = "getUserById")
        public void updateName(@CacheKey String id, String name) {
            storage.get(id).setName(name);
        }

    }

}
