package com.netflix.hystrix.contrib.servostream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;

import com.netflix.servo.Metric;
import com.netflix.servo.publish.MetricObserver;
import com.netflix.servo.tag.Tag;
import com.netflix.servo.tag.TagList;

/**
 * Groups Servo metrics into a MetricGroup and then writes them to HttpServletResponse in text/event-stream format.
 */
public class HystrixEventStreamMetricsObserver implements MetricObserver {

    private final HashMap<String, MetricGroup> metricsGroups = new HashMap<String, MetricGroup>();
    private final HashMap<String, String> metricsCache = new HashMap<String, String>();
    private final String CurrentTime = "currentTime";
    private final HttpServletResponse response;
    private volatile boolean isRunning = true;

    HystrixEventStreamMetricsObserver(HttpServletResponse response) {
        this.response = response;
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void update(List<Metric> metrics) {
        try {
            List<JSONObject> groupedJson = getServoMetricsGroupedAsJson(metrics);

            for (JSONObject json : groupedJson) {
                response.getWriter().println("data: " + json.toString() + "\n");
            }
            response.flushBuffer();
        } catch (Exception e) {
            HystrixServoPoller.logger.error("Failed to write metric group.", e);
            // the servlet itself will handle closing the connection using 'isRunning'
            isRunning = false;
        }
    }

    @Override
    public String getName() {
        return HystrixEventStreamMetricsObserver.class.getSimpleName();
    }

    private List<JSONObject> getServoMetricsGroupedAsJson(List<Metric> metrics) {
        List<JSONObject> events = new ArrayList<JSONObject>();

        for (Metric metric : metrics) {

            String type = null;
            String id = null;
            TagList tagList = metric.getConfig().getTags();
            if (tagList != null) {
                Tag tag = tagList.getTag("type");
                if (tag != null) {
                    type = tag.getValue();
                }
                tag = tagList.getTag("instance");
                if (tag != null) {
                    id = tag.getValue();
                }
            }

            String cacheKey = type + id;

            MetricGroup group = metricsGroups.get(cacheKey);
            if (group == null) {
                group = new MetricGroup(type, id);
                metricsGroups.put(cacheKey, group);
            }

            try {
                group.addMetricFields(metric);
            } catch (Throwable t) {
                HystrixServoPoller.logger.error("Caught failure when adding metric: " + metric + " to group: " + group, t);
            }
        }

        for (MetricGroup mg : metricsGroups.values()) {
            try {
                if (mg.isDirty()) {
                    // ok we have data in the metric group e.g HystrixCommand : CinematchGetRatings
                    // but we should check with a cache and see if the metric has changed. Do not send data that has not changed

                    JSONObject json = mg.getJsonObject();

                    long currentTime = -1;
                    if (json.has(CurrentTime)) {
                        currentTime = (Long) json.remove(CurrentTime);
                    }
                    String jsonString = json.toString();
                    String cacheKey = mg.getCacheKey();
                    String prev = metricsCache.get(cacheKey);

                    if (prev == null || (prev != null && !prev.equals(jsonString))) {
                        metricsCache.put(cacheKey, jsonString);
                        if (currentTime != -1) {
                            json.put(CurrentTime, currentTime);
                        }
                        events.add(json);
                    }

                    mg.clear();
                }
            } catch (JSONException e) {
                HystrixServoPoller.logger.error("Caught failure when getting json from group: " + mg, e);
            }
        }
        return events;
    }

}