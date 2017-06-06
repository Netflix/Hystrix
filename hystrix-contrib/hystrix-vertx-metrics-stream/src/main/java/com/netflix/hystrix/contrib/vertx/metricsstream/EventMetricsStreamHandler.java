package com.netflix.hystrix.contrib.vertx.metricsstream;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static com.netflix.hystrix.contrib.vertx.metricsstream.JsonMappers.toJson;

/**
 * {@link Handler} implementing a SSE for Hystrix Metrics.
 *
 * @author Kennedy Oliveira
 */
public class EventMetricsStreamHandler implements Handler<RoutingContext> {

  private static final Logger log = LoggerFactory.getLogger(EventMetricsStreamHandler.class);

  /**
   * Default path for metrics stream.
   */
  public static final String DEFAULT_HYSTRIX_PREFIX = "/hystrix.stream";
  /**
   * Default interval if none was especified.
   */
  private static final int DEFAULT_DELAY = 500;

  /**
   * The payload header to be concatenated before the payload
   */
  private final static Buffer PAYLOAD_HEADER = Buffer.buffer("data: ".getBytes(StandardCharsets.UTF_8));

  /**
   * The payload footer, to be concatenated after the payload
   */
  private final static Buffer PAYLOAD_FOOTER = Buffer.buffer(new byte[]{10, 10});

  /**
   * Dynamic Max Concurrent Connections
   */
  private final static DynamicIntProperty maxConcurrentConnections = DynamicPropertyFactory.getInstance().getIntProperty("hystrix.stream.maxConcurrentConnections", 5);

  /**
   * Actual concurrent connections
   */
  private final static AtomicInteger concurrentConnections = new AtomicInteger(0);

  /**
   * Creates a new {@link EventMetricsStreamHandler}
   *
   * @return the new created {@link EventMetricsStreamHandler}
   */
  public static EventMetricsStreamHandler createHandler() {
    return new EventMetricsStreamHandler();
  }

  @Override
  public void handle(RoutingContext routingContext) {
    log.debug("[Vertx-EventMetricsStream] New connection {}:{}", routingContext.request().remoteAddress().host(), routingContext.request().remoteAddress().port());
    final Vertx vertx = routingContext.vertx();
    final HttpServerRequest request = routingContext.request();
    final HttpServerResponse response = routingContext.response();

    final int currentConnections = concurrentConnections.incrementAndGet();
    final int maxConnections = maxConcurrentConnections.get();
    log.debug("[Vertx-EventMetricsStream] Current Connections - {} / Max Connections {}", currentConnections, maxConnections);

    if (currentConnections > maxConnections) {
      response.setStatusCode(503);
      response.end("Max concurrent connections reached: " + maxConnections);
      concurrentConnections.decrementAndGet();
    } else {
      response.setChunked(true);
      response.setStatusCode(200);
      response.headers()
              .add(HttpHeaders.CONTENT_TYPE, "text/event-stream;charset=UTF-8")
              .add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate")
              .add("Pragma", "no-cache");

      long delay = DEFAULT_DELAY;

      final String requestDelay = request.getParam("delay");
      try {
        if (requestDelay != null && !requestDelay.isEmpty()) {
          delay = Math.max(Long.parseLong(requestDelay), 1);
        }
      } catch (Exception e) {
        log.warn("[Vertx-EventMetricsStream] Error parsing the delay parameter [{}]", requestDelay);
      }

      final long comandsMetrics = vertx.setPeriodic(delay, ignored -> {
        log.trace("[Vertx-EventMetricsStream] Sending metrics");
        try {
          log.trace("[Vertx-EventMetricsStream] Fetching and writing command metrics...");
          for (HystrixCommandMetrics commandMetrics : HystrixCommandMetrics.getInstances()) {
            writeMetric(toJson(commandMetrics), response);
          }
          log.trace("[Vertx-EventMetricsStream] Finished sending the metrics");
        } catch (Exception e) {
          log.error("[Vertx-EventMetricsStream] Sending metrics stream", e);
        }
      });

      final long threadPoolsMetric = vertx.setPeriodic(delay, ignored -> {
        log.trace("[Vertx-EventMetricsStream] Fetching and writing thread pool metrics...");
        try {
          for (HystrixThreadPoolMetrics threadPoolMetrics : HystrixThreadPoolMetrics.getInstances()) {
            writeMetric(toJson(threadPoolMetrics), response);
          }
        } catch (Exception e) {
          log.error("[Vertx-EventMetricsStream] Sending metrics stream", e);
        }
      });

      final long collapserMetric = vertx.setPeriodic(delay, ignored -> {
        log.trace("[Vertx-EventMetricsStream] Fetching and writing collapser metrics...");
        try {
          for (HystrixCollapserMetrics collapserMetrics : HystrixCollapserMetrics.getInstances()) {
            writeMetric(toJson(collapserMetrics), response);
          }
        } catch (Exception e) {
          log.error("[Vertx-EventMetricsStream] Sending metrics stream", e);
        }
      });

      final long[] timerIds = {comandsMetrics, threadPoolsMetric, collapserMetric};

      response.closeHandler(ignored -> {
        log.debug("[Vertx-EventMetricsStream] - Client closed connection, stopping sending metrics");
        handleClosedConnection(vertx, timerIds);
      });

      request.exceptionHandler(ig -> {
        log.error("[Vertx-EventMetricsStream] Sending metrics, stopping sending metrics", ig);
        handleClosedConnection(vertx, timerIds);
      });
    }
  }

  /**
   * Handle the connection that has been closed by the client or by some error.
   *
   * @param vertx    {@link Vertx} instance that the connection was registered from.
   * @param timerIds Timer's IDs for cancellation.
   */
  private void handleClosedConnection(Vertx vertx, long[] timerIds) {
    final int currentConnections = concurrentConnections.decrementAndGet();
    cancelTimers(vertx, timerIds);

    log.debug("[Vertx-EventMetricsStream] Current Connections - {} / Max Connections {}", currentConnections, maxConcurrentConnections.get());
  }

  /**
   * Cancel all the timers from {@code timerIds}.
   *
   * @param vertx    {@link Vertx} instance that the timers was registered.
   * @param timerIds Timer's IDs for cancellation.
   */
  private void cancelTimers(Vertx vertx, long[] timerIds) {
    if (timerIds != null && timerIds.length > 0) {
      for (long timerId : timerIds) {
        vertx.cancelTimer(timerId);
      }
    }
  }

  /**
   * Write the data to the event stream consumer.
   *
   * @param data     Data to be written.
   * @param response Response object to write data to.
   */
  private void writeMetric(String data, HttpServerResponse response) {
    response.write(PAYLOAD_HEADER);
    response.write(Buffer.buffer(data.getBytes(StandardCharsets.UTF_8)));
    response.write(PAYLOAD_FOOTER);
  }
}
