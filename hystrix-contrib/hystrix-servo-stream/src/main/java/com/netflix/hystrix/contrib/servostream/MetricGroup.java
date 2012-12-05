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
package com.netflix.hystrix.contrib.servostream;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.servo.Metric;
import com.netflix.servo.monitor.MonitorConfig;

/**
 * Used to convert a list of Servo metrics to a list of JSON objects.
 * <p>
 * We use Servo tags to group a set of flat metrics into groups of common metric such as
 * for a HystrixCommand or HystrixThreadPool.
 */
class MetricGroup {

    private static final Logger logger = LoggerFactory.getLogger(MetricGroup.class);

    private static final String MetricType = "metric-type";
    private static final String Clazz = "class";
    private static final String Name = "name";
    private static final String True = "true";
    private static final String False = "false";

    private String name;
    private final String clazz;
    private String type;
    private final Map<String, Number> nAttrs;
    private final Map<String, String> sAttrs;

    // boolean to track metric group state(s) especially since we re-use these objects for performance reasons
    private boolean isDirty = false;

    public MetricGroup(String clazzz, String nameString) {
        this.clazz = clazzz;
        this.name = nameString;
        if (name == null) {
            name = clazz;
        }
        this.type = clazz;
        this.nAttrs = new HashMap<String, Number>();
        this.sAttrs = new HashMap<String, String>();
    }

    void addMetricFields(Metric m) {

        Object value = m.getValue();

        if (value instanceof Number) {
            isDirty = true;
            nAttrs.put(m.getConfig().getName(), (Number) value);
        } else if (value instanceof String) {
            isDirty = true;
            sAttrs.put(m.getConfig().getName(), (String) value);
        } else if (value instanceof Boolean) {
            isDirty = true;
            sAttrs.put(m.getConfig().getName(), ((Boolean) value).toString());
        } else {
            try {
                sAttrs.put(m.getConfig().getName(), value.toString());
                isDirty = true;
            } catch (Throwable t) {
                logger.warn("Could not add metric: " + m.getConfig().toString(), t);
            }
        }
    }

    String getCacheKey() {
        return clazz + name;
    }

    JSONObject getJsonObject() throws JSONException {

        this.sAttrs.put(Name, name);
        this.sAttrs.put(MetricType, type);
        this.sAttrs.put(Clazz, clazz);

        JSONObject json = new JSONObject();

        for (String key : nAttrs.keySet()) {
            Number n = nAttrs.get(key);
            if (n != null) {
                try {
                    json.put(key, n);
                } catch (JSONException e) {
                    //logger.error("Could not add metric attr: " + key + " " + n, e);
                }
            }
        }

        for (String key : sAttrs.keySet()) {
            String value = sAttrs.get(key);
            if (value == null) {
                continue;
            }
            if (value.equals(True) || value.equals(False)) {
                json.put(key, Boolean.valueOf(value));
            } else {
                json.put(key, value);
            }
        }

        return json;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((clazz == null) ? 0 : clazz.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        MetricGroup other = (MetricGroup) obj;
        boolean equals = (this.clazz != null) ? this.clazz.equals(other.clazz) : other.clazz == null;
        equals &= (this.name != null) ? this.name.equals(other.name) : other.name == null;
        return equals;
    }

    @Override
    public String toString() {
        return "MetricGroup [name=" + name + ", clazz=" + clazz + ", type="
                + type + "]";
    }

    public void clear() {
        nAttrs.clear();
        sAttrs.clear();
        isDirty = false;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public static class UnitTest {

        @Test
        public void testEquals() throws Exception {

            MetricGroup group1 = new MetricGroup("foo", "bar");
            MetricGroup group2 = new MetricGroup("foo", "bar");

            assertEquals(group1, group2);

            MetricGroup group3 = new MetricGroup("foo", null);

            assertFalse(group2.equals(group3));

            MetricGroup group4 = new MetricGroup("bar", null);

            assertFalse(group2.equals(group4));
            assertFalse(group3.equals(group4));

            MetricGroup group5 = new MetricGroup("bar", null);
            assertFalse(group2.equals(group5));
            assertFalse(group3.equals(group5));
            assertTrue(group4.equals(group5));
        }

        @Test
        public void testAddMeticsThenClearAndThenReAddMetrics() throws Exception {

            MetricGroup group = new MetricGroup("foo", "bar");

            group.addMetricFields(constructMetric("n1", 1));
            group.addMetricFields(constructMetric("n2", 2));
            group.addMetricFields(constructMetric("n3", 3));
            group.addMetricFields(constructMetric("s1", "v1"));
            group.addMetricFields(constructMetric("s2", "v2"));
            group.addMetricFields(constructMetric("s3", "v3"));

            JSONObject json = group.getJsonObject();

            assertEquals("foo", json.getString("metric-type"));
            assertEquals("bar", json.getString("name"));
            assertEquals(1, json.getInt("n1"));
            assertEquals(2, json.getInt("n2"));
            assertEquals(3, json.getInt("n3"));
            assertEquals("v1", json.getString("s1"));
            assertEquals("v2", json.getString("s2"));
            assertEquals("v3", json.getString("s3"));

            group.clear();
            json = group.getJsonObject();

            assertEquals("foo", json.getString("metric-type"));
            assertEquals("bar", json.getString("name"));
            assertFalse(json.has("n1"));
            assertFalse(json.has("n2"));
            assertFalse(json.has("n3"));
            assertFalse(json.has("s1"));
            assertFalse(json.has("s2"));
            assertFalse(json.has("s3"));

            group.addMetricFields(constructMetric("s1", "v1"));
            json = group.getJsonObject();
            assertEquals("v1", json.getString("s1"));
        }

        private Metric constructMetric(String name, Object value) {

            MonitorConfig config = MonitorConfig.builder(name).build();
            return new Metric(config, System.currentTimeMillis(), value);
        }
    }
}