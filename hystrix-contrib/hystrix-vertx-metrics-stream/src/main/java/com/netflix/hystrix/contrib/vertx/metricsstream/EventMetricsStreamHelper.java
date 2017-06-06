package com.netflix.hystrix.contrib.vertx.metricsstream;

import io.vertx.core.*;
import io.vertx.core.metrics.MetricsOptions;

import java.util.Objects;

/**
 * Helper Class with some methods to create {@link io.vertx.core.Vertx} and {@link EventMetricsStreamVerticle}
 * for easy deploy.
 *
 * @author Kennedy Oliveira
 */
public class EventMetricsStreamHelper {
  /**
   * Utility class, no instances for you!
   */
  private EventMetricsStreamHelper() { }

  /**
   * <p>Deploy a single instance of {@link EventMetricsStreamVerticle} in the specified {@link Vertx}.</p>
   * <p>If you pass a {@link Handler} in the {@code completionFuture} param, the handler will be called after the instance is deployed with an error or success that you can check
   * in {@link AsyncResult#succeeded()}.</p>
   * <p>By default the server started will listen in {@code 8099} port, and answer in the path {@link EventMetricsStreamHandler#DEFAULT_HYSTRIX_PREFIX}, you can override
   * both behavior with Archaius properties {@code hystrix.vertx.stream.httpServer.port} and {@code hystrix.vertx.stream.httpServer.path} respectively.</p>
   *
   * @param vertx            {@link Vertx} instance to deploy the {@link EventMetricsStreamVerticle}
   * @param completionFuture {@link Handler} to be notified when the deploy is done either with error or success.
   */
  public static void deployStandaloneMetricsStream(Vertx vertx, Handler<AsyncResult<String>> completionFuture) {
    Objects.requireNonNull(vertx, "The Vertx instance can't be null!");

    final EventMetricsStreamVerticle verticle = new EventMetricsStreamVerticle();
    final DeploymentOptions options = new DeploymentOptions().setInstances(1);
    if (completionFuture != null) {
      vertx.deployVerticle(verticle, options, completionFuture);
    } else {
      vertx.deployVerticle(verticle, options);
    }
  }

  /**
   * <p>Easy method to start a {@link Vertx} instance and deploy a single instance of {@link EventMetricsStreamVerticle}.</p>
   * <p>The {@link Vertx} instance will use only one thread in the event pool.</p>
   * <p>By default the server started will listen in {@code 8099} port, and answer in the path {@link EventMetricsStreamHandler#DEFAULT_HYSTRIX_PREFIX}, you can override
   * both behavior with Archaius properties {@code hystrix.vertx.stream.httpServer.port} and {@code hystrix.vertx.stream.httpServer.path} respectively.</p>
   */
  public static void deployStandaloneMetricsStream() {
    final Vertx vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(1).setMetricsOptions(new MetricsOptions().setEnabled(false)));
    deployStandaloneMetricsStream(vertx, null);
  }
}
