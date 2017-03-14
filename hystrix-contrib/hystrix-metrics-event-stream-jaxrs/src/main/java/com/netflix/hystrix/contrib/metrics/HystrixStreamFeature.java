/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.hystrix.contrib.metrics;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

import com.netflix.hystrix.contrib.metrics.controller.HystrixConfigSseController;
import com.netflix.hystrix.contrib.metrics.controller.HystrixMetricsStreamController;
import com.netflix.hystrix.contrib.metrics.controller.HystrixRequestEventsSseController;
import com.netflix.hystrix.contrib.metrics.controller.HystrixUtilizationSseController;

/**
 * @author justinjose28
 * 
 */
public class HystrixStreamFeature implements Feature {

	@Override
	public boolean configure(FeatureContext context) {
		context.register(new HystrixMetricsStreamController());
		context.register(new HystrixUtilizationSseController());
		context.register(new HystrixRequestEventsSseController());
		context.register(new HystrixConfigSseController());
		context.register(HystrixStreamingOutputProvider.class);
		return true;
	}

}
