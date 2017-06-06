package com.netflix.hystrix.contrib.vertx.metricsstream;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;

import static com.netflix.hystrix.contrib.vertx.metricsstream.EventMetricsStreamHandler.DEFAULT_HYSTRIX_PREFIX;

/**
 * <p>{@link io.vertx.core.Verticle} that enables the metrics to be consumed via {@code Vertx}.</p>
 *
 * @author Kennedy Oliveira
 */
public class EventMetricsStreamVerticle extends AbstractVerticle {

  /**
   * Http Server Port, defaults to {@code 8099}.
   */
  private final static DynamicIntProperty httpServerPort = DynamicPropertyFactory.getInstance().getIntProperty("hystrix.vertx.stream.httpServer.port", 8099);

  /**
   * Hystrix Metrics Stream Path, defaults to {@link EventMetricsStreamHandler#DEFAULT_HYSTRIX_PREFIX}.
   */
  private final static DynamicStringProperty hystrixStreamPath = DynamicPropertyFactory.getInstance()
                                                                                       .getStringProperty("hystrix.vertx.stream.httpServer.path", DEFAULT_HYSTRIX_PREFIX);

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    // build a custom router with the hystrix event metric handler
    final Router router = Router.router(vertx);

    router.route(hystrixStreamPath.get())
          .method(HttpMethod.GET)
          .handler(EventMetricsStreamHandler.createHandler());

    // creates the HttpServer to listen to
    vertx.createHttpServer()
         .requestHandler(router::accept)
         .listen(httpServerPort.get(), result -> {
           if (result.succeeded()) {
             startFuture.complete();
           } else {
             startFuture.fail(result.cause());
           }
         });
  }
}
