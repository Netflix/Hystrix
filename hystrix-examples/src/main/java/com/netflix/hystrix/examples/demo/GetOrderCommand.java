/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.examples.demo;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;

/**
 * Sample HystrixCommand simulating one that would fetch Order objects from a remote service or database.
 * <p>
 * This fails fast with no fallback and does not use request caching.
 */
public class GetOrderCommand extends HystrixCommand<Order> {

    private final int orderId;

    public GetOrderCommand(int orderId) {
        super(HystrixCommandGroupKey.Factory.asKey("Order"));
        this.orderId = orderId;
    }

    @Override
    protected Order run() {
        /* simulate performing network call to retrieve order */
        try {
            Thread.sleep((int) (Math.random() * 200) + 50);
        } catch (InterruptedException e) {
            // do nothing
        }

        /* fail rarely ... but allow failure as this one has no fallback */
        if (Math.random() > 0.9999) {
            throw new RuntimeException("random failure loading order over network");
        }

        /* latency spike 5% of the time */
        if (Math.random() > 0.95) {
            // random latency spike
            try {
                Thread.sleep((int) (Math.random() * 300) + 25);
            } catch (InterruptedException e) {
                // do nothing
            }
        }

        /* success ... create Order with data "from" the remote service response */
        return new Order(orderId);
    }

}
