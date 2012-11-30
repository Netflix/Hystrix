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
 * Sample HystrixCommand simulating one that would fetch PaymentInformation objects from a remote service or database.
 * <p>
 * This fails fast with no fallback and does not use request caching.
 */
public class GetPaymentInformationCommand extends HystrixCommand<PaymentInformation> {

    private final UserAccount user;

    public GetPaymentInformationCommand(UserAccount user) {
        super(HystrixCommandGroupKey.Factory.asKey("PaymentInformation"));
        this.user = user;
    }

    @Override
    protected PaymentInformation run() {
        /* simulate performing network call to retrieve order */
        try {
            Thread.sleep((int) (Math.random() * 20) + 5);
        } catch (InterruptedException e) {
            // do nothing
        }

        /* fail rarely ... but allow failure */
        if (Math.random() > 0.9999) {
            throw new RuntimeException("random failure loading payment information over network");
        }

        /* latency spike 2% of the time */
        if (Math.random() > 0.98) {
            // random latency spike
            try {
                Thread.sleep((int) (Math.random() * 100) + 25);
            } catch (InterruptedException e) {
                // do nothing
            }
        }

        /* success ... create (a very insecure) PaymentInformation with data "from" the remote service response */
        return new PaymentInformation(user, "4444888833337777", 12, 15);
    }

}
