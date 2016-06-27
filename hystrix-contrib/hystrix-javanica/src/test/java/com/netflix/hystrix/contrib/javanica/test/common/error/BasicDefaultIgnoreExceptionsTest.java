package com.netflix.hystrix.contrib.javanica.test.common.error;

import com.netflix.hystrix.contrib.javanica.annotation.DefaultProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.junit.Before;
import org.junit.Test;

/**
 * todo [dmgcodevil]: add docs
 * <p>
 * Created by dmgcodevil.
 */
public abstract class BasicDefaultIgnoreExceptionsTest {
    private UserService userService;

    @Before
    public void setUp() throws Exception {
        userService = createUserService();
    }

    protected abstract UserService createUserService();

    @Test(expected = IllegalArgumentException.class)
    public void testDefaultIgnoreException() {
        userService.getById("");
    }

    @DefaultProperties(ignoreExceptions = IllegalArgumentException.class)
    public static class UserService {
        @HystrixCommand
        public User getById(String id) throws IllegalArgumentException {
            if (id == null || "".equals(id)) {
                throw new IllegalArgumentException("bad id");
            }
            return new User(id);
        }
    }

    public static class User {
        private String id;

        public User(String id) {
            this.id = id;
        }
    }
}
