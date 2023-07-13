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

import com.netflix.hystrix.util.InternMap;

/**
 * A key to represent a {@link HystrixCollapser} for monitoring, circuit-breakers, metrics publishing, caching and other such uses.
 * <p>
 * This interface is intended to work natively with Enums so that implementing code can be an enum that implements this interface.
 */
public interface HystrixCollapserKey extends HystrixKey {
    class Factory {
        private Factory() {
        }

        // used to intern instances so we don't keep re-creating them millions of times for the same key
        private static final InternMap<String, HystrixCollapserKey> intern
                = new InternMap<String, HystrixCollapserKey>(
                new InternMap.ValueConstructor<String, HystrixCollapserKey>() {
                    @Override
                    public HystrixCollapserKey create(String key) {
                        return new HystrixCollapserKeyDefault(key);
                    }
                });

        /**
         * Retrieve (or create) an interned HystrixCollapserKey instance for a given name.
         * 
         * @param name collapser name
         * @return HystrixCollapserKey instance that is interned (cached) so a given name will always retrieve the same instance.
         */
        public static HystrixCollapserKey asKey(String name) {
            return intern.interned(name);
        }

        private static class HystrixCollapserKeyDefault extends HystrixKey.HystrixKeyDefault implements HystrixCollapserKey {
            public HystrixCollapserKeyDefault(String name) {
                super(name);
            }
        }

        /* package-private */ static int getCollapserCount() {
            return intern.size();
        }
    }
}
