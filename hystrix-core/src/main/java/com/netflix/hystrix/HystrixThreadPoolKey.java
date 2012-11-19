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
 * A key to represent a {@link HystrixThreadPool} for monitoring, metrics publishing, caching and other such uses.
 * <p>
 * This interface is intended to work natively with Enums so that implementing code can be an enum that implements this interface.
 */
public interface HystrixThreadPoolKey {

    /**
     * The 'key' used as the name for a thread-pool.
     * <p>
     * The word 'name' is used instead of 'key' so that Enums can implement this interface and it work natively.
     * 
     * @return String
     */
    public String name();

    public static class Factory {

        private Factory() {
        }

        // used to intern instances so we don't keep re-creating them millions of times for the same key
        private static ConcurrentHashMap<String, HystrixThreadPoolKey> intern = new ConcurrentHashMap<String, HystrixThreadPoolKey>();

        /**
         * Retrieve (or create) an interned HystrixThreadPoolKey instance for a given name.
         * 
         * @param name
         * @return HystrixThreadPoolKey instance that is interned (cached) so a given name will always retrieve the same instance.
         */
        public static HystrixThreadPoolKey asKey(String name) {
            HystrixThreadPoolKey k = intern.get(name);
            if (k == null) {
                intern.putIfAbsent(name, new HystrixThreadPoolKeyDefault(name));
            }
            return intern.get(name);
        }

        private static class HystrixThreadPoolKeyDefault implements HystrixThreadPoolKey {

            private String name;

            private HystrixThreadPoolKeyDefault(String name) {
                this.name = name;
            }

            @Override
            public String name() {
                return name;
            }

        }
    }
}