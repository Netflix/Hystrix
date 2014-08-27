/**
 * Copyright 2013 Netflix, Inc.
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

package com.netflix.hystrix.contrib.codahalemetricspublisher;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.netflix.config.DynamicPropertyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of @MetricFilter based upon an Archaius DynamicPropertyFactory
 *
 * To enable this filter, the property 'filter.graphite.metrics' must be set to TRUE
 *
 * If this is the case, metrics will be filtered unless METRIC_NAME = true is set in
 * the properties
 *
 *
 *  eg HystrixCommand.IndiciaService.GetIndicia.countFailure = true
 *
 *
 * For detail on how the metric names are constructed, refer to the source of the
 *
 * {@link HystrixCodaHaleMetricsPublisherCommand}
 *
 * and
 *
 * {@link HystrixCodaHaleMetricsPublisherThreadPool}
 *
 * classes.
 *
 *  @author Simon Irving
 */
public class ConfigurableCodaHaleMetricFilter implements MetricFilter{

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurableCodaHaleMetricFilter.class);

    private DynamicPropertyFactory archaiusPropertyFactory;


    public ConfigurableCodaHaleMetricFilter(DynamicPropertyFactory archaiusPropertyFactory)
    {
        this.archaiusPropertyFactory = archaiusPropertyFactory;
    }

    @Override
    public boolean matches(String s, Metric metric) {

        if (!isFilterEnabled())
        {
            return true;
        }

        boolean matchesFilter = archaiusPropertyFactory.getBooleanProperty(s, false).get();

        LOGGER.debug("Does metric [{}] match filter? [{}]",s,matchesFilter);

        return matchesFilter;
    }

    protected boolean isFilterEnabled() {

        boolean filterEnabled = archaiusPropertyFactory.getBooleanProperty("filter.graphite.metrics", false).get();

        LOGGER.debug("Is filter enabled? [{}]", filterEnabled);

        return filterEnabled;
    }


}
