package com.netflix.hystrix.contrib.javanica.test.spring.cache;

import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.aop.aspectj.HystrixCacheAspect;
import com.netflix.hystrix.contrib.javanica.cache.DefaultHystrixGeneratedCacheKey;
import com.netflix.hystrix.contrib.javanica.cache.HystrixCacheKeyGenerator;
import com.netflix.hystrix.contrib.javanica.cache.HystrixGeneratedCacheKey;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import com.netflix.hystrix.contrib.javanica.test.spring.domain.User;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.PostConstruct;
import javax.cache.annotation.CacheInvocationParameter;
import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheKeyInvocationContext;
import javax.cache.annotation.CacheRemove;
import javax.cache.annotation.CacheResult;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.netflix.hystrix.contrib.javanica.CommonUtils.getLastExecutedCommand;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test to check cache implementation based on JSR-107.
 *
 * @author dmgcodevil
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, Jsr107CacheTest.CacheTestConfig.class})
public class Jsr107CacheTest {

    private UserService userService;

    @Autowired
    private ApplicationContext applicationContext;

    @Before
    public void setUp() throws Exception {
        userService = applicationContext.getBean(UserService.class);
    }

    /**
     * Get-Set-Get with Request Cache Invalidation Test.
     * <p/>
     * given:
     * command to get user by id, see {@link UserService#getUserById(String)}
     * command to update user, see {@link UserService#update(com.netflix.hystrix.contrib.javanica.test.spring.domain.User)}
     * <p/>
     * when:
     * 1. call {@link UserService#getUserById(String)}
     * 2. call {@link UserService#getUserById(String)}
     * 3. call {@link UserService#update(com.netflix.hystrix.contrib.javanica.test.spring.domain.User)}
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
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {

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


        } finally {
            context.shutdown();
        }

        // start a new request context
        context = HystrixRequestContext.initializeContext();
        try {
            User user = userService.getUserById("1");
            HystrixInvokableInfo<?> getUserByIdCommand = getLastExecutedCommand();
            assertEquals("1", user.getId());
            // this is a new request context so this
            // should not come from cache
            assertFalse(getUserByIdCommand.isResponseFromCache());
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testGetSetGetUserCache_givenOneCommandAndOneMethodAnnotatedWithCacheRemove() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {

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


        } finally {
            context.shutdown();
        }

        // start a new request context
        context = HystrixRequestContext.initializeContext();
        try {
            User user = userService.getUserById("1");
            HystrixInvokableInfo<?> getUserByIdCommand = getLastExecutedCommand();
            assertEquals("1", user.getId());
            // this is a new request context so this
            // should not come from cache
            assertFalse(getUserByIdCommand.isResponseFromCache());
        } finally {
            context.shutdown();
        }
    }

    public static class UserService {
        private Map<String, User> storage = new ConcurrentHashMap<String, User>();

        @PostConstruct
        private void init() {
            storage.put("1", new User("1", "name"));
        }

        @CacheResult
        @HystrixCommand
        public User getUserById(@CacheKey String id) {
            return storage.get(id);
        }

        @CacheRemove(cacheName = "getUserById", cacheKeyGenerator = UserCacheKeyGenerator.class)
        @HystrixCommand
        public void update(@CacheKey User user) {
            storage.put(user.getId(), user);
        }

        @CacheRemove(cacheName = "getUserById")
        public void updateName(@CacheKey String id, String name) {
            storage.get(id).setName(name);
        }

    }

    public static class UserCacheKeyGenerator implements HystrixCacheKeyGenerator {
        @Override
        public HystrixGeneratedCacheKey generateCacheKey(CacheKeyInvocationContext<? extends Annotation> cacheKeyInvocationContext) {
            CacheInvocationParameter cacheInvocationParameter = cacheKeyInvocationContext.getKeyParameters()[0];
            User user = (User) cacheInvocationParameter.getValue();
            return new DefaultHystrixGeneratedCacheKey(user.getId());
        }
    }

    /**
     * Spring configuration.
     */
    @Configurable
    public static class CacheTestConfig {
        @Bean
        @Scope(value = "prototype")
        public UserService userService() {
            return new UserService();
        }

        @Bean
        public HystrixCacheAspect hystrixCacheAspect() {
            return new HystrixCacheAspect();
        }
    }

}
