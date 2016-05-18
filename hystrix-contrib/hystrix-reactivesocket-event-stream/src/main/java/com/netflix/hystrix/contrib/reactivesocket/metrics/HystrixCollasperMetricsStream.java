package com.netflix.hystrix.contrib.reactivesocket.metrics;


import com.fasterxml.jackson.core.JsonGenerator;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.contrib.reactivesocket.StreamingSupplier;
import org.agrona.LangUtil;
import rx.functions.Func0;

import java.io.ByteArrayOutputStream;
import java.util.stream.Stream;

public class HystrixCollasperMetricsStream extends StreamingSupplier<HystrixCollapserMetrics> {
    private static HystrixCollasperMetricsStream INSTANCE = new HystrixCollasperMetricsStream();

    private HystrixCollasperMetricsStream() {
        super();
    }

    public static HystrixCollasperMetricsStream getInstance() {
        return INSTANCE;
    }

    @Override
    protected Stream getStream() {
        return HystrixCollapserMetrics.getInstances().stream();
    }

    protected byte[] getPayloadData(final HystrixCollapserMetrics collapserMetrics) {
        byte[] retVal = null;
        try {
            HystrixCollapserKey key = collapserMetrics.getCollapserKey();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            JsonGenerator json = jsonFactory.createGenerator(bos);
            json.writeStartObject();

            json.writeStringField("type", "HystrixCollapser");
            json.writeStringField("name", key.name());
            json.writeNumberField("currentTime", System.currentTimeMillis());

            safelyWriteNumberField(json, "rollingCountRequestsBatched", new Func0<Long>() {
                @Override
                public Long call() {
                    return collapserMetrics.getRollingCount(HystrixEventType.Collapser.ADDED_TO_BATCH);
                }
            });
            safelyWriteNumberField(json, "rollingCountBatches", new Func0<Long>() {
                @Override
                public Long call() {
                    return collapserMetrics.getRollingCount(HystrixEventType.Collapser.BATCH_EXECUTED);
                }
            });
            safelyWriteNumberField(json, "rollingCountResponsesFromCache", new Func0<Long>() {
                @Override
                public Long call() {
                    return collapserMetrics.getRollingCount(HystrixEventType.Collapser.RESPONSE_FROM_CACHE);
                }
            });

            // batch size percentiles
            json.writeNumberField("batchSize_mean", collapserMetrics.getBatchSizeMean());
            json.writeObjectFieldStart("batchSize");
            json.writeNumberField("25", collapserMetrics.getBatchSizePercentile(25));
            json.writeNumberField("50", collapserMetrics.getBatchSizePercentile(50));
            json.writeNumberField("75", collapserMetrics.getBatchSizePercentile(75));
            json.writeNumberField("90", collapserMetrics.getBatchSizePercentile(90));
            json.writeNumberField("95", collapserMetrics.getBatchSizePercentile(95));
            json.writeNumberField("99", collapserMetrics.getBatchSizePercentile(99));
            json.writeNumberField("99.5", collapserMetrics.getBatchSizePercentile(99.5));
            json.writeNumberField("100", collapserMetrics.getBatchSizePercentile(100));
            json.writeEndObject();

            // shard size percentiles (commented-out for now)
            //json.writeNumberField("shardSize_mean", collapserMetrics.getShardSizeMean());
            //json.writeObjectFieldStart("shardSize");
            //json.writeNumberField("25", collapserMetrics.getShardSizePercentile(25));
            //json.writeNumberField("50", collapserMetrics.getShardSizePercentile(50));
            //json.writeNumberField("75", collapserMetrics.getShardSizePercentile(75));
            //json.writeNumberField("90", collapserMetrics.getShardSizePercentile(90));
            //json.writeNumberField("95", collapserMetrics.getShardSizePercentile(95));
            //json.writeNumberField("99", collapserMetrics.getShardSizePercentile(99));
            //json.writeNumberField("99.5", collapserMetrics.getShardSizePercentile(99.5));
            //json.writeNumberField("100", collapserMetrics.getShardSizePercentile(100));
            //json.writeEndObject();

            //json.writeNumberField("propertyValue_metricsRollingStatisticalWindowInMilliseconds", collapserMetrics.getProperties().metricsRollingStatisticalWindowInMilliseconds().get());
            json.writeBooleanField("propertyValue_requestCacheEnabled", collapserMetrics.getProperties().requestCacheEnabled().get());
            json.writeNumberField("propertyValue_maxRequestsInBatch", collapserMetrics.getProperties().maxRequestsInBatch().get());
            json.writeNumberField("propertyValue_timerDelayInMilliseconds", collapserMetrics.getProperties().timerDelayInMilliseconds().get());

            json.writeNumberField("reportingHosts", 1); // this will get summed across all instances in a cluster

            json.writeEndObject();
            json.close();

            retVal = bos.toByteArray();
        } catch (Exception e) {
            LangUtil.rethrowUnchecked(e);
        }

        return retVal;
    }

}
