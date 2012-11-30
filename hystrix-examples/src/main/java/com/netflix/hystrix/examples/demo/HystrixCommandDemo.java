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

import java.math.BigDecimal;
import java.net.HttpCookie;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

/**
 * Executable client that demonstrates the lifecycle, metrics, request log and behavior of HystrixCommands.
 */
public class HystrixCommandDemo {

    public static void main(String args[]) {
        /*
         * Instead of using injected properties we'll set them via Archaius
         * so the rest of the code behaves as it would in a real system
         * where it picks up properties externally provided.
         */
        ConfigurationManager.getConfigInstance().setProperty("hystrix.threadpool.default.coreSize", 8);
        ConfigurationManager.getConfigInstance().setProperty("hystrix.command.CreditCardCommand.execution.isolation.thread.timeoutInMilliseconds", 3000);
        ConfigurationManager.getConfigInstance().setProperty("hystrix.command.GetUserAccountCommand.execution.isolation.thread.timeoutInMilliseconds", 50);

        new HystrixCommandDemo().run();

    }

    /*
     * Thread-pool to simulate HTTP requests.
     * 
     * Use CallerRunsPolicy so we can just keep iterating and adding to it and it will block when full.
     */
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(5, 5, 5, TimeUnit.DAYS, new SynchronousQueue<Runnable>(), new ThreadPoolExecutor.CallerRunsPolicy());

    public void run() {
        while (true) {
            executeSimulatedUserRequestForOrderConfirmationAndCreditCardPayment();
        }
    }

    public void executeSimulatedUserRequestForOrderConfirmationAndCreditCardPayment() {
        pool.execute(new Runnable() {

            @Override
            public void run() {
                HystrixRequestContext context = HystrixRequestContext.initializeContext();
                try {
                    /* fetch user object with http cookies */
                    UserAccount user = new GetUserAccountCommand(new HttpCookie("mockKey", "mockValueFromHttpRequest")).execute();

                    /* fetch the payment information (asynchronously) for the user so the credit card payment can proceed */
                    Future<PaymentInformation> paymentInformation = new GetPaymentInformationCommand(user).queue();

                    /* fetch the order we're processing for the user */
                    int orderIdFromRequestArgument = 13579;
                    Order previouslySavedOrder = new GetOrderCommand(orderIdFromRequestArgument).execute();

                    CreditCardCommand credit = new CreditCardCommand(previouslySavedOrder, paymentInformation.get(), new BigDecimal(123.45));
                    credit.execute();

                    System.out.println("Request => " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    context.shutdown();
                }
            }

        });

    }
}
