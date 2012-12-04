package com.netflix.hystrix.contrib.servostream;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.publish.MetricFilter;
import com.netflix.servo.publish.MonitorRegistryMetricPoller;
import com.netflix.servo.publish.PollRunnable;
import com.netflix.servo.tag.Tag;
import com.netflix.servo.tag.TagList;

/**
 * Polls Servo for Hystrix metrics and sends them to a MetricsObserver.
 */
public class HystrixServoPoller {

    static final Logger logger = LoggerFactory.getLogger(HystrixServoPoller.class);
    private final MonitorRegistryMetricPoller monitorPoller;
    private final ScheduledExecutorService executor;
    private final int delay;

    public HystrixServoPoller(int delay) {
        executor = new ScheduledThreadPoolExecutor(1, new TurbineMetricsPollerThreadFactory());
        monitorPoller = new MonitorRegistryMetricPoller(DefaultMonitorRegistry.getInstance(), 1, TimeUnit.MINUTES, false);
        this.delay = delay;
    }

    public synchronized void start(HystrixEventStreamMetricsObserver observer) {
        logger.info("Starting HystrixServoPoller");
        PollRunnable task = new PollRunnable(monitorPoller, new HystrixMetricFilter(), observer);
        executor.scheduleWithFixedDelay(task, 0, delay, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        logger.info("Stopping the Servo Metrics Poller");
        executor.shutdownNow();
        if (monitorPoller != null) {
            monitorPoller.shutdown();
        }
    }

    private class TurbineMetricsPollerThreadFactory implements ThreadFactory {
        private static final String MetricsThreadName = "ServoMetricPoller";

        private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();

        public Thread newThread(Runnable r) {
            Thread thread = defaultFactory.newThread(r);
            thread.setName(MetricsThreadName);
            return thread;
        }
    }

    private class HystrixMetricFilter implements MetricFilter {

        private HystrixMetricFilter() {
        }

        @Override
        public boolean matches(MonitorConfig mConfig) {

            TagList tagList = mConfig.getTags();
            if (tagList != null) {
                Tag classTag = tagList.getTag("type");
                logger.info("HystrixMetricFilter matches: " + classTag);
                if (classTag == null) {
                    return false;
                }
                if (classTag.getValue().startsWith("Hystrix")) {
                    return true;
                }
            }

            return false;
        }
    }

}
