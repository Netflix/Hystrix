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
 * A key to represent a {@link HystrixCommand} for monitoring, circuit-breakers, metrics publishing, caching and other such uses.
 * <p>
 * This interface is intended to work natively with Enums so that implementing code can be an enum that implements this interface.
 */
public interface HystrixCommandKey extends HystrixKey {
    class Factory {
        private Factory() {
        }

        // used to intern instances so we don't keep re-creating them millions of times for the same key
        private static final InternMap<String, HystrixCommandKeyDefault> intern
                = new InternMap<String, HystrixCommandKeyDefault>(
                new InternMap.ValueConstructor<String, HystrixCommandKeyDefault>() {
                    @Override
                    public HystrixCommandKeyDefault create(String key) {
                        return new HystrixCommandKeyDefault(key);
                    }
                });


        /**
         * Retrieve (or create) an interned HystrixCommandKey instance for a given name.
         * 
         * @param name command name
         * @return HystrixCommandKey instance that is interned (cached) so a given name will always retrieve the same instance.
         */
        public static HystrixCommandKey asKey(String name) {
            return intern.interned(name);
        }

        private static class HystrixCommandKeyDefault extends HystrixKey.HystrixKeyDefault implements HystrixCommandKey {
            public HystrixCommandKeyDefault(String name) {
                super(name);
            }
        }

        /* package-private */ static int getCommandCount() {
            return intern.size();
        }
    }

}
