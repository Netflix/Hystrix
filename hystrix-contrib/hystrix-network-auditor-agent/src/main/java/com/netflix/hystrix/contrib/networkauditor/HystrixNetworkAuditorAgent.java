/**
 * Copyright 2013 Netflix, Inc.
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
package com.netflix.hystrix.contrib.networkauditor;

import java.lang.instrument.Instrumentation;

/**
 * Java Agent to instrument network code in the java.* libraries and use Hystrix state to determine if calls are Hystrix-isolated or not.
 */
public class HystrixNetworkAuditorAgent {

    private static final HystrixNetworkAuditorEventListener DEFAULT_BRIDGE = new HystrixNetworkAuditorEventListener() {

        @Override
        public void handleNetworkEvent() {
            // do nothing
        }

    };
    private static volatile HystrixNetworkAuditorEventListener bridge = DEFAULT_BRIDGE;

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new NetworkClassTransform());
    }

    /**
     * Invoked by instrumented code when network access occurs.
     */
    public static void notifyOfNetworkEvent() {
        bridge.handleNetworkEvent();
    }

    /**
     * Register the implementation of the {@link HystrixNetworkAuditorEventListener} that will receive the events.
     * 
     * @param bridge
     *            {@link HystrixNetworkAuditorEventListener} implementation
     */
    public static void registerEventListener(HystrixNetworkAuditorEventListener bridge) {
        if (bridge == null) {
            throw new IllegalArgumentException("HystrixNetworkAuditorEventListener can not be NULL");
        }
        HystrixNetworkAuditorAgent.bridge = bridge;
    }

}
