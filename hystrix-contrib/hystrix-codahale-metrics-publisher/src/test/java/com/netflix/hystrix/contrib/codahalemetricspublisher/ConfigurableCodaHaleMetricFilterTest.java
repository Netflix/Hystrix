package com.netflix.hystrix.contrib.codahalemetricspublisher;

import com.codahale.metrics.Metric;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test the ConfigurableCodaHaleMetricFilter
 *
 * @author Simon Irving
 */
public class ConfigurableCodaHaleMetricFilterTest {

    private Metric metric = mock(Metric.class);

    private final static DynamicPropertyFactory archiausPropertyFactory = mock(DynamicPropertyFactory.class);

    private static final DynamicBooleanProperty DYNAMIC_BOOLEAN_TRUE = mock(DynamicBooleanProperty.class);
    private static final DynamicBooleanProperty DYNAMIC_BOOLEAN_FALSE = mock(DynamicBooleanProperty.class);

    @BeforeClass
    public static void initialiseMocks()
    {
        when(archiausPropertyFactory.getBooleanProperty(any(String.class), any(Boolean.class))).thenReturn(DYNAMIC_BOOLEAN_FALSE);
        when(archiausPropertyFactory.getBooleanProperty(eq("this.metric.is.allowed"), any(Boolean.class))).thenReturn(DYNAMIC_BOOLEAN_TRUE);
        when(DYNAMIC_BOOLEAN_TRUE.get()).thenReturn(true);
        when(DYNAMIC_BOOLEAN_FALSE.get()).thenReturn(false);
    }

    @After
    public void assertMetricsNotTouched()
    {
        verifyZeroInteractions(metric);
    }

    @Test
    public void testMetricConfiguredInFilterWithFilterEnabled()
    {
        when(archiausPropertyFactory.getBooleanProperty(eq("filter.graphite,metrics"), any(Boolean.class))).thenReturn(DYNAMIC_BOOLEAN_TRUE);
        ConfigurableCodaHaleMetricFilter filter = new ConfigurableCodaHaleMetricFilter(archiausPropertyFactory);
        assertTrue(filter.matches("this.metric.is.allowed", metric));
    }

    @Test
    public void testMetricConfiguredInFilterWithFilterDisabled()
    {
        when(archiausPropertyFactory.getBooleanProperty(eq("filter.graphite,metrics"), any(Boolean.class))).thenReturn(DYNAMIC_BOOLEAN_FALSE);
        ConfigurableCodaHaleMetricFilter filter = new ConfigurableCodaHaleMetricFilter(archiausPropertyFactory);
        assertTrue(filter.matches("this.metric.is.allowed", metric));
    }

    @Test
    public void testMetricNotConfiguredInFilterWithFilterEnabled()
    {
        when(archiausPropertyFactory.getBooleanProperty(eq("filter.graphite,metrics"), any(Boolean.class))).thenReturn(DYNAMIC_BOOLEAN_TRUE);
        ConfigurableCodaHaleMetricFilter filter = new ConfigurableCodaHaleMetricFilter(archiausPropertyFactory);
        assertFalse(filter.matches("this.metric.is.not.allowed", metric));
    }

    @Test
    public void testMetricNotConfiguredInFilterWithFilterDisabled()
    {
        when(archiausPropertyFactory.getBooleanProperty(eq("filter.graphite,metrics"), any(Boolean.class))).thenReturn(DYNAMIC_BOOLEAN_FALSE);
        ConfigurableCodaHaleMetricFilter filter = new ConfigurableCodaHaleMetricFilter(archiausPropertyFactory);
        assertTrue(filter.matches("this.metric.is.not.allowed", metric));
    }
}
