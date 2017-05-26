package com.netflix.hystrix.contrib.codahalemetricspublisher;

import com.codahale.metrics.MetricRegistry;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.strategy.HystrixPlugins;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class HystrixCodaHaleMetricsPublisherCommandTest {
    private final MetricRegistry metricRegistry = new MetricRegistry();

    @Before
    public void setup() {
        HystrixPlugins.getInstance().registerMetricsPublisher(new HystrixCodaHaleMetricsPublisher("hystrix", metricRegistry));
    }

    @Test
    public void testCommandSuccess() throws InterruptedException {
        Command command = new Command();
        command.execute();

        Thread.sleep(1000);

        assertThat((Long) metricRegistry.getGauges().get("hystrix.testGroup.testCommand.countSuccess").getValue(), is(1L));
        assertThat((Long) metricRegistry.getGauges().get("hystrix.HystrixThreadPool.threadGroup.totalTaskCount").getValue(), is(1L));

    }

    private static class Command extends HystrixCommand<Void> {
        final static HystrixCommandKey hystrixCommandKey = HystrixCommandKey.Factory.asKey("testCommand");
        final static HystrixCommandGroupKey hystrixCommandGroupKey = HystrixCommandGroupKey.Factory.asKey("testGroup");
        final static HystrixThreadPoolKey hystrixThreadPool = HystrixThreadPoolKey.Factory.asKey("threadGroup");

        Command() {
            super(Setter.withGroupKey(hystrixCommandGroupKey).andCommandKey(hystrixCommandKey).andThreadPoolKey(hystrixThreadPool));
        }

        @Override
        protected Void run() throws Exception {
            return null;
        }
    }
}
