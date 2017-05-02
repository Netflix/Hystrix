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
package com.netflix.hystrix.contrib.javanica.test.hk2;

import javax.inject.Singleton;

import org.glassfish.hk2.api.InterceptionService;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

/**
 * 
 * @author justinjose28
 *
 */
public class Hk2TestUtils {

	public static ServiceLocator getServiceLocator() {
		return ServiceLocatorUtilities.bind(new AbstractBinder() {

			@Override
			protected void configure() {
				bind(CommandInterceptionService.class).to(InterceptionService.class).in(Singleton.class);
				bind(CacheRemoveInterceptionService.class).to(InterceptionService.class).in(Singleton.class);
			}

		});
	}

}
