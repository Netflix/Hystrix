/**
 * Copyright 2012 Netflix, Inc.
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

import java.util.concurrent.ConcurrentHashMap;

/**
 * A key to represent a {@link HystrixCommand} for monitoring, circuit-breakers, metrics publishing, caching and other such uses.
 * <p>
 * This interface is intended to work natively with Enums so that implementing code can be an enum that implements this interface.
 */
public interface HystrixCommandKey {

    /**
     * The word 'name' is used instead of 'key' so that Enums can implement this interface and it work natively.
     * 
     * @return String
     */
    public String name();

    public static class Factory {

        private Factory() {
        }

        // used to intern instances so we don't keep re-creating them millions of times for the same key
        private static ConcurrentHashMap<String, HystrixCommandKey> intern = new ConcurrentHashMap<String, HystrixCommandKey>();

        /**
         * Retrieve (or create) an interned HystrixCommandKey instance for a given name.
         * 
         * @param name
         * @return HystrixCommandKey instance that is interned (cached) so a given name will always retrieve the same instance.
         */
        public static HystrixCommandKey asKey(String name) {
            HystrixCommandKey k = intern.get(name);
            if (k == null) {
                intern.putIfAbsent(name, new HystrixCommandKeyDefault(name));
            }
            return intern.get(name);
        }

        private static class HystrixCommandKeyDefault implements HystrixCommandKey {

            private String name;

            private HystrixCommandKeyDefault(String name) {
                this.name = name;
            }

            @Override
            public String name() {
                return name;
            }

        }
    }

}
