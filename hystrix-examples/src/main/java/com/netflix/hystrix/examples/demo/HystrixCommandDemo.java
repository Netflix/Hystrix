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
package com.netflix.hystrix.examples.demo;

import java.math.BigDecimal;
import java.net.HttpCookie;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandMetrics.HealthCounts;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

/**
 * Executable client that demonstrates the lifecycle, metrics, request log and behavior of HystrixCommands.
 */
public class HystrixCommandDemo {

    public static void main(String args[]) {
        new HystrixCommandDemo().startDemo();
    }

    public HystrixCommandDemo() {
        /*
         * Instead of using injected properties we'll set them via Archaius
         * so the rest of the code behaves as it would in a real system
         * where it picks up properties externally provided.
         */
        ConfigurationManager.getConfigInstance().setProperty("hystrix.threadpool.default.coreSize", 8);
        ConfigurationManager.getConfigInstance().setProperty("hystrix.command.CreditCardCommand.execution.isolation.thread.timeoutInMilliseconds", 3000);
        ConfigurationManager.getConfigInstance().setProperty("hystrix.command.GetUserAccountCommand.execution.isolation.thread.timeoutInMilliseconds", 50);
        // set the rolling percentile more granular so we see data change every second rather than every 10 seconds as is the default 
        ConfigurationManager.getConfigInstance().setProperty("hystrix.command.default.metrics.rollingPercentile.numBuckets", 60);
    }

    /*
     * Thread-pool to simulate HTTP requests.
     * 
     * Use CallerRunsPolicy so we can just keep iterating and adding to it and it will block when full.
     */
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(5, 5, 5, TimeUnit.DAYS, new SynchronousQueue<Runnable>(), new ThreadPoolExecutor.CallerRunsPolicy());

    public void startDemo() {
        startMetricsMonitor();
        while (true) {
            runSimulatedRequestOnThread();
        }
    }

    public void runSimulatedRequestOnThread() {
        pool.execute(new Runnable() {

            @Override
            public void run() {
                HystrixRequestContext context = HystrixRequestContext.initializeContext();
                try {
                    executeSimulatedUserRequestForOrderConfirmationAndCreditCardPayment();

                    System.out.println("Request => " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    context.shutdown();
                }
            }

        });
    }

    public void executeSimulatedUserRequestForOrderConfirmationAndCreditCardPayment() throws InterruptedException, ExecutionException {
        /* fetch user object with http cookies */
        UserAccount user = new GetUserAccountCommand(new HttpCookie("mockKey", "mockValueFromHttpRequest")).execute();

        /* fetch the payment information (asynchronously) for the user so the credit card payment can proceed */
        Future<PaymentInformation> paymentInformation = new GetPaymentInformationCommand(user).queue();

        /* fetch the order we're processing for the user */
        int orderIdFromRequestArgument = 13579;
        Order previouslySavedOrder = new GetOrderCommand(orderIdFromRequestArgument).execute();

        CreditCardCommand credit = new CreditCardCommand(previouslySavedOrder, paymentInformation.get(), new BigDecimal(123.45));
        credit.execute();
    }

    public void startMetricsMonitor() {
        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    /**
                     * Since this is a simple example and we know the exact HystrixCommandKeys we are interested in
                     * we will retrieve the HystrixCommandMetrics objects directly.
                     * 
                     * Typically you would instead retrieve metrics from where they are published which is by default
                     * done using Servo: https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring
                     */

                    // wait 5 seconds on each loop
                    try {
                        Thread.sleep(5000);
                    } catch (Exception e) {
                        // ignore
                    }

                    // we are using default names so can use class.getSimpleName() to derive the keys
                    HystrixCommandMetrics creditCardMetrics = HystrixCommandMetrics.getInstance(HystrixCommandKey.Factory.asKey(CreditCardCommand.class.getSimpleName()));
                    HystrixCommandMetrics orderMetrics = HystrixCommandMetrics.getInstance(HystrixCommandKey.Factory.asKey(GetOrderCommand.class.getSimpleName()));
                    HystrixCommandMetrics userAccountMetrics = HystrixCommandMetrics.getInstance(HystrixCommandKey.Factory.asKey(GetUserAccountCommand.class.getSimpleName()));
                    HystrixCommandMetrics paymentInformationMetrics = HystrixCommandMetrics.getInstance(HystrixCommandKey.Factory.asKey(GetPaymentInformationCommand.class.getSimpleName()));

                    // print out metrics
                    StringBuilder out = new StringBuilder();
                    out.append("\n");
                    out.append("#####################################################################################").append("\n");
                    out.append("# CreditCardCommand: " + getStatsStringFromMetrics(creditCardMetrics)).append("\n");
                    out.append("# GetOrderCommand: " + getStatsStringFromMetrics(orderMetrics)).append("\n");
                    out.append("# GetUserAccountCommand: " + getStatsStringFromMetrics(userAccountMetrics)).append("\n");
                    out.append("# GetPaymentInformationCommand: " + getStatsStringFromMetrics(paymentInformationMetrics)).append("\n");
                    out.append("#####################################################################################").append("\n");
                    System.out.println(out.toString());
                }
            }

            private String getStatsStringFromMetrics(HystrixCommandMetrics metrics) {
                StringBuilder m = new StringBuilder();
                if (metrics != null) {
                    HealthCounts health = metrics.getHealthCounts();
                    m.append("Requests: ").append(health.getTotalRequests()).append(" ");
                    m.append("Errors: ").append(health.getErrorCount()).append(" (").append(health.getErrorPercentage()).append("%)   ");
                    m.append("Mean: ").append(metrics.getExecutionTimePercentile(50)).append(" ");
                    m.append("75th: ").append(metrics.getExecutionTimePercentile(75)).append(" ");
                    m.append("90th: ").append(metrics.getExecutionTimePercentile(90)).append(" ");
                    m.append("99th: ").append(metrics.getExecutionTimePercentile(99)).append(" ");
                }
                return m.toString();
            }

        });
        t.setDaemon(true);
        t.start();
    }
}
