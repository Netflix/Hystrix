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
package com.netflix.hystrix.contrib.javanica.test.common.configuration.command;

import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.test.common.BasicHystrixTest;
import com.netflix.hystrix.contrib.javanica.test.common.domain.User;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.CIRCUIT_BREAKER_ENABLED;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.CIRCUIT_BREAKER_ERROR_THRESHOLD_PERCENTAGE;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.CIRCUIT_BREAKER_FORCE_CLOSED;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.CIRCUIT_BREAKER_FORCE_OPEN;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.CIRCUIT_BREAKER_REQUEST_VOLUME_THRESHOLD;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.CIRCUIT_BREAKER_SLEEP_WINDOW_IN_MILLISECONDS;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.EXECUTION_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.EXECUTION_ISOLATION_STRATEGY;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.EXECUTION_ISOLATION_THREAD_INTERRUPT_ON_TIMEOUT;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.EXECUTION_ISOLATION_THREAD_TIMEOUT_IN_MILLISECONDS;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.EXECUTION_TIMEOUT_ENABLED;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.FALLBACK_ENABLED;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.FALLBACK_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.METRICS_HEALTH_SNAPSHOT_INTERVAL_IN_MILLISECONDS;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.METRICS_ROLLING_PERCENTILE_BUCKET_SIZE;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.METRICS_ROLLING_PERCENTILE_ENABLED;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.METRICS_ROLLING_PERCENTILE_NUM_BUCKETS;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.METRICS_ROLLING_STATS_TIME_IN_MILLISECONDS;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.METRICS_ROLLING_PERCENTILE_TIME_IN_MILLISECONDS;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.METRICS_ROLLING_STATS_NUM_BUCKETS;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.REQUEST_CACHE_ENABLED;
import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.REQUEST_LOG_ENABLED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by dmgcodevil
 */
public abstract class BasicCommandPropertiesTest extends BasicHystrixTest {

    private UserService userService;

    protected abstract UserService createUserService();

    @Before
    public void setUp() throws Exception {
        userService = createUserService();
    }

    @Test
    public void testGetUser() throws NoSuchFieldException, IllegalAccessException {
        User u1 = userService.getUser("1", "name: ");
        assertEquals("name: 1", u1.getName());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals("GetUserCommand", command.getCommandKey().name());
        assertEquals("UserGroupKey", command.getCommandGroup().name());
        assertEquals("Test", command.getThreadPoolKey().name());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        // assert properties
        assertEquals(110, command.getProperties().executionTimeoutInMilliseconds().get().intValue());
        assertEquals(false, command.getProperties().executionIsolationThreadInterruptOnTimeout().get());

        HystrixThreadPoolProperties properties = getThreadPoolProperties(command);

        assertEquals(30, (int) properties.coreSize().get());
        assertEquals(35, (int) properties.maximumSize().get());
        assertEquals(true, properties.getAllowMaximumSizeToDivergeFromCoreSize().get());
        assertEquals(101, (int) properties.maxQueueSize().get());
        assertEquals(2, (int) properties.keepAliveTimeMinutes().get());
        assertEquals(15, (int) properties.queueSizeRejectionThreshold().get());
        assertEquals(1440, (int) properties.metricsRollingStatisticalWindowInMilliseconds().get());
        assertEquals(12, (int) properties.metricsRollingStatisticalWindowBuckets().get());
    }

    @Test
    public void testGetUserDefaultPropertiesValues() {
        User u1 = userService.getUserDefProperties("1", "name: ");
        assertEquals("name: 1", u1.getName());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals("getUserDefProperties", command.getCommandKey().name());
        assertEquals("UserService", command.getCommandGroup().name());
        assertEquals("UserService", command.getThreadPoolKey().name());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void testGetUserDefGroupKeyWithSpecificThreadPoolKey() {
        User u1 = userService.getUserDefGroupKeyWithSpecificThreadPoolKey("1", "name: ");
        assertEquals("name: 1", u1.getName());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals("getUserDefGroupKeyWithSpecificThreadPoolKey", command.getCommandKey().name());
        assertEquals("UserService", command.getCommandGroup().name());
        assertEquals("CustomThreadPool", command.getThreadPoolKey().name());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void testHystrixCommandProperties() {
        User u1 = userService.getUsingAllCommandProperties("1", "name: ");
        assertEquals("name: 1", u1.getName());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        // assert properties
        assertEquals(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE, command.getProperties().executionIsolationStrategy().get());
        assertEquals(500, command.getProperties().executionTimeoutInMilliseconds().get().intValue());
        assertEquals(true, command.getProperties().executionTimeoutEnabled().get().booleanValue());
        assertEquals(false, command.getProperties().executionIsolationThreadInterruptOnTimeout().get().booleanValue());
        assertEquals(10, command.getProperties().executionIsolationSemaphoreMaxConcurrentRequests().get().intValue());
        assertEquals(15, command.getProperties().fallbackIsolationSemaphoreMaxConcurrentRequests().get().intValue());
        assertEquals(false, command.getProperties().fallbackEnabled().get().booleanValue());
        assertEquals(false, command.getProperties().circuitBreakerEnabled().get().booleanValue());
        assertEquals(30, command.getProperties().circuitBreakerRequestVolumeThreshold().get().intValue());
        assertEquals(250, command.getProperties().circuitBreakerSleepWindowInMilliseconds().get().intValue());
        assertEquals(60, command.getProperties().circuitBreakerErrorThresholdPercentage().get().intValue());
        assertEquals(false, command.getProperties().circuitBreakerForceOpen().get().booleanValue());
        assertEquals(true, command.getProperties().circuitBreakerForceClosed().get().booleanValue());
        assertEquals(false, command.getProperties().metricsRollingPercentileEnabled().get().booleanValue());
        assertEquals(400, command.getProperties().metricsRollingPercentileWindowInMilliseconds().get().intValue());
        assertEquals(5, command.getProperties().metricsRollingPercentileWindowBuckets().get().intValue());
        assertEquals(6, command.getProperties().metricsRollingPercentileBucketSize().get().intValue());
        assertEquals(10, command.getProperties().metricsRollingStatisticalWindowBuckets().get().intValue());
        assertEquals(500, command.getProperties().metricsRollingStatisticalWindowInMilliseconds().get().intValue());
        assertEquals(312, command.getProperties().metricsHealthSnapshotIntervalInMilliseconds().get().intValue());
        assertEquals(false, command.getProperties().requestCacheEnabled().get().booleanValue());
        assertEquals(true, command.getProperties().requestLogEnabled().get().booleanValue());
    }

    public static class UserService {

        @HystrixCommand(commandKey = "GetUserCommand", groupKey = "UserGroupKey", threadPoolKey = "Test",
                commandProperties = {
                        @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "110"),
                        @HystrixProperty(name = "execution.isolation.thread.interruptOnTimeout", value = "false")
                },
                threadPoolProperties = {
                        @HystrixProperty(name = "coreSize", value = "30"),
                        @HystrixProperty(name = "maximumSize", value = "35"),
                        @HystrixProperty(name = "allowMaximumSizeToDivergeFromCoreSize", value = "true"),
                        @HystrixProperty(name = "maxQueueSize", value = "101"),
                        @HystrixProperty(name = "keepAliveTimeMinutes", value = "2"),
                        @HystrixProperty(name = "metrics.rollingStats.numBuckets", value = "12"),
                        @HystrixProperty(name = "queueSizeRejectionThreshold", value = "15"),
                        @HystrixProperty(name = "metrics.rollingStats.timeInMilliseconds", value = "1440")
                })
        public User getUser(String id, String name) {
            return new User(id, name + id); // it should be network call
        }

        @HystrixCommand
        public User getUserDefProperties(String id, String name) {
            return new User(id, name + id); // it should be network call
        }

        @HystrixCommand(threadPoolKey = "CustomThreadPool")
        public User getUserDefGroupKeyWithSpecificThreadPoolKey(String id, String name) {
            return new User(id, name + id); // it should be network call
        }

        @HystrixCommand(
                commandProperties = {
                        @HystrixProperty(name = EXECUTION_ISOLATION_STRATEGY, value = "SEMAPHORE"),
                        @HystrixProperty(name = EXECUTION_ISOLATION_THREAD_TIMEOUT_IN_MILLISECONDS, value = "500"),
                        @HystrixProperty(name = EXECUTION_TIMEOUT_ENABLED, value = "true"),
                        @HystrixProperty(name = EXECUTION_ISOLATION_THREAD_INTERRUPT_ON_TIMEOUT, value = "false"),
                        @HystrixProperty(name = EXECUTION_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS, value = "10"),
                        @HystrixProperty(name = FALLBACK_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS, value = "15"),
                        @HystrixProperty(name = FALLBACK_ENABLED, value = "false"),
                        @HystrixProperty(name = CIRCUIT_BREAKER_ENABLED, value = "false"),
                        @HystrixProperty(name = CIRCUIT_BREAKER_REQUEST_VOLUME_THRESHOLD, value = "30"),
                        @HystrixProperty(name = CIRCUIT_BREAKER_SLEEP_WINDOW_IN_MILLISECONDS, value = "250"),
                        @HystrixProperty(name = CIRCUIT_BREAKER_ERROR_THRESHOLD_PERCENTAGE, value = "60"),
                        @HystrixProperty(name = CIRCUIT_BREAKER_FORCE_OPEN, value = "false"),
                        @HystrixProperty(name = CIRCUIT_BREAKER_FORCE_CLOSED, value = "true"),
                        @HystrixProperty(name = METRICS_ROLLING_PERCENTILE_ENABLED, value = "false"),
                        @HystrixProperty(name = METRICS_ROLLING_PERCENTILE_TIME_IN_MILLISECONDS, value = "400"),
                        @HystrixProperty(name = METRICS_ROLLING_STATS_TIME_IN_MILLISECONDS, value = "500"),
                        @HystrixProperty(name = METRICS_ROLLING_STATS_NUM_BUCKETS, value = "10"),
                        @HystrixProperty(name = METRICS_ROLLING_PERCENTILE_NUM_BUCKETS, value = "5"),
                        @HystrixProperty(name = METRICS_ROLLING_PERCENTILE_BUCKET_SIZE, value = "6"),
                        @HystrixProperty(name = METRICS_HEALTH_SNAPSHOT_INTERVAL_IN_MILLISECONDS, value = "312"),
                        @HystrixProperty(name = REQUEST_CACHE_ENABLED, value = "false"),
                        @HystrixProperty(name = REQUEST_LOG_ENABLED, value = "true")
                }
        )
        public User getUsingAllCommandProperties(String id, String name) {
            return new User(id, name + id); // it should be network call
        }

    }
}
