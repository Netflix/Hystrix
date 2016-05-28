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
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandMetrics.HealthCounts;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.plugins.RxJavaPlugins;
import rx.plugins.RxJavaSchedulersHook;

/**
 * Executable client that demonstrates the lifecycle, metrics, request log and behavior of HystrixCommands.
 */
public class HystrixCommandAsyncDemo {

//    public static void main(String args[]) {
//        new HystrixCommandAsyncDemo().startDemo(true);
//    }

    static class ContextAwareRxSchedulersHook extends RxJavaSchedulersHook {
        @Override
        public Action0 onSchedule(final Action0 initialAction) {
            final Runnable initialRunnable = new Runnable() {
                @Override
                public void run() {
                    initialAction.call();
                }
            };
            final Runnable wrappedRunnable =
                    new HystrixContextRunnable(initialRunnable);
            return new Action0() {
                @Override
                public void call() {
                    wrappedRunnable.run();
                }
            };
        }
    }

    public HystrixCommandAsyncDemo() {
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

        RxJavaPlugins.getInstance().registerSchedulersHook(new ContextAwareRxSchedulersHook());
    }

    public void startDemo(final boolean shouldLog) {
        startMetricsMonitor(shouldLog);
        while (true) {
            final HystrixRequestContext context = HystrixRequestContext.initializeContext();
            Observable<CreditCardAuthorizationResult> o = observeSimulatedUserRequestForOrderConfirmationAndCreditCardPayment();

            final CountDownLatch latch = new CountDownLatch(1);
            o.subscribe(new Subscriber<CreditCardAuthorizationResult>() {
                @Override
                public void onCompleted() {
                    latch.countDown();
                    context.shutdown();
                }

                @Override
                public void onError(Throwable e) {
                    e.printStackTrace();
                    latch.countDown();
                    context.shutdown();
                }

                @Override
                public void onNext(CreditCardAuthorizationResult creditCardAuthorizationResult) {
                    if (shouldLog) {
                        System.out.println("Request => " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
                    }
                }
            });

            try {
                latch.await(5000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                System.out.println("INTERRUPTED!");
            }
        }
    }

    private final static Random r = new Random();

    private class Pair<A, B> {
        private final A a;
        private final B b;

        Pair(A a, B b) {
            this.a = a;
            this.b = b;
        }

        A a() {
            return this.a;
        }

        B b() {
            return this.b;
        }
    }

    public Observable<CreditCardAuthorizationResult> observeSimulatedUserRequestForOrderConfirmationAndCreditCardPayment() {
        /* fetch user object with http cookies */
        try {
            Observable<UserAccount> user = new GetUserAccountCommand(new HttpCookie("mockKey", "mockValueFromHttpRequest")).observe();
            /* fetch the payment information (asynchronously) for the user so the credit card payment can proceed */
            Observable<PaymentInformation> paymentInformation = user.flatMap(new Func1<UserAccount, Observable<PaymentInformation>>() {
                @Override
                public Observable<PaymentInformation> call(UserAccount userAccount) {
                    return new GetPaymentInformationCommand(userAccount).observe();
                }
            });

            /* fetch the order we're processing for the user */
            int orderIdFromRequestArgument = 13579;
            final Observable<Order> previouslySavedOrder = new GetOrderCommand(orderIdFromRequestArgument).observe();

            return Observable.zip(paymentInformation, previouslySavedOrder, new Func2<PaymentInformation, Order, Pair<PaymentInformation, Order>>() {
                @Override
                public Pair<PaymentInformation, Order> call(PaymentInformation paymentInformation, Order order) {
                    return new Pair<PaymentInformation, Order>(paymentInformation, order);
                }
            }).flatMap(new Func1<Pair<PaymentInformation, Order>, Observable<CreditCardAuthorizationResult>>() {
                @Override
                public Observable<CreditCardAuthorizationResult> call(Pair<PaymentInformation, Order> pair) {
                    return new CreditCardCommand(pair.b(), pair.a(), new BigDecimal(123.45)).observe();
                }
            });
        } catch (IllegalArgumentException ex) {
            return Observable.error(ex);
        }


    }

    public void startMetricsMonitor(final boolean shouldLog) {
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

                    if (shouldLog) {
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
