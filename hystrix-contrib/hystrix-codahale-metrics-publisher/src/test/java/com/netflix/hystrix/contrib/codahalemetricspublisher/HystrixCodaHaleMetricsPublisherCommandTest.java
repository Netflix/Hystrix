package com.netflix.hystrix.contrib.codahalemetricspublisher;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.strategy.HystrixPlugins;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

public class HystrixCodaHaleMetricsPublisherCommandTest {
    private final MetricRegistry metricRegistry = new MetricRegistry();

    @Before
    public void setup() {
        HystrixPlugins.getInstance().registerMetricsPublisher(new HystrixCodaHaleMetricsPublisher(metricRegistry));
    }

    @After
    public void teardown() {
        HystrixPlugins.reset();
    }

    @Test
    public void commandMaxActiveGauge() {
        final HystrixCommandKey hystrixCommandKey = HystrixCommandKey.Factory.asKey("test");
        final HystrixCommandGroupKey hystrixCommandGroupKey = HystrixCommandGroupKey.Factory.asKey("test");

        new HystrixCommand<Void>(HystrixCommand.Setter
                .withGroupKey(hystrixCommandGroupKey)
                .andCommandKey(hystrixCommandKey)) {
            @Override
            protected Void run() throws Exception {
                return null;
            }
        }.execute();

        for (Map.Entry<String, Gauge> entry : metricRegistry.getGauges().entrySet()) {
            entry.getValue().getValue();
        }
    }
}
