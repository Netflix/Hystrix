package com.netflix.hystrix.contrib.javanica.test.spring.cache;

import javax.cache.annotation.CacheResult;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.cache.DefaultCacheKeyGenerator;
import com.netflix.hystrix.contrib.javanica.test.spring.domain.User;

/**
 * // todo
 *
 * @author dmgcodevil
 */
public class Jsr107CacheTest {

	// todo create test

	public static class UserService {

		@CacheResult(cacheKeyGenerator = DefaultCacheKeyGenerator.class)
		@HystrixCommand(cacheKeyMethod = "getUserIdCacheKey")
		public User getUser(String id, String name) {
			return new User(id, name + id); // it should be network call
		}

		private String getUserIdCacheKey(String id, String name) {
			return id + name;
		}
	}


}
