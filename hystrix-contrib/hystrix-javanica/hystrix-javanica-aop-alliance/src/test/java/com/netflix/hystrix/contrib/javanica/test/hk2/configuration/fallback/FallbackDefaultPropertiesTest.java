/**
 * Copyright 2017 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.test.hk2.configuration.fallback;

import static org.glassfish.hk2.utilities.ServiceLocatorUtilities.bind;

import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.BeforeClass;

import com.netflix.hystrix.contrib.javanica.test.common.configuration.fallback.BasicFallbackDefaultPropertiesTest;
import com.netflix.hystrix.contrib.javanica.test.hk2.Hk2TestUtils;


public class FallbackDefaultPropertiesTest extends BasicFallbackDefaultPropertiesTest {

	private static ServiceLocator serv;

	@BeforeClass
	public static void setup() {
		serv = Hk2TestUtils.getServiceLocator();
		bind(serv, new AbstractBinder() {
			@Override
			protected void configure() {
				bindAsContract(Service.class).in(PerLookup.class);

			}
		});
	}

	@Override
	protected Service createService() {
		return serv.getService(Service.class);
	}
}
