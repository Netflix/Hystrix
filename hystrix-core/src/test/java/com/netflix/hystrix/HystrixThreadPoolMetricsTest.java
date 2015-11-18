/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;

public class HystrixThreadPoolMetricsTest {

	@Before
	public void resetAll() {
		HystrixThreadPoolMetrics.reset();
	}

	@Test
	public void shouldYieldNoExecutedTasksOnStartup() throws Exception {
		//given
		final Collection<HystrixThreadPoolMetrics> instances = HystrixThreadPoolMetrics.getInstances();

		//then
		assertEquals(0, instances.size());

	}
	@Test
	public void shouldReturnOneExecutedTask() throws Exception {
		//given
		final Collection<HystrixThreadPoolMetrics> instances = HystrixThreadPoolMetrics.getInstances();

		//when
		new NoOpHystrixCommand().execute();

		//then
		Thread.sleep(100);
		assertEquals(1, instances.size());
		assertEquals(1, instances.iterator().next().getRollingCountThreadsExecuted());
	}

	private static class NoOpHystrixCommand extends HystrixCommand<Void> {
		public NoOpHystrixCommand() {
			super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("HystrixThreadPoolMetrics-UnitTest"))
                    .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter().withMetricsRollingStatisticalWindowInMilliseconds(100)));
		}

		@Override
		protected Void run() throws Exception {
			System.out.println("Run in thread : " + Thread.currentThread().getName());
            return null;
		}
	}


}