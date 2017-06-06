# hystrix-vertx-metrics-stream

This module exposes metrics in a [text/event-stream](https://developer.mozilla.org/en-US/docs/Server-sent_events/Using_server-sent_events) formatted stream, using a [Vertx](http://vertx.io/) HttpServer, that continues as long as a client holds the connection.

This modules let you embed the HttpServer in any application, doesn't matter if is a Java SE desktop, even command line application, doesn't need a servlet container, is extremely efficient and easy to deploy, using little memory and like 3 threads. 

Each HystrixCommand instance will emit data such as this (without line breaks):

```json
data: {
    "type": "HystrixCommand",
    "name": "fetch-qrcode-rx",
    "group": "remote",
    "currentTime": 1458251618270,
    "isCircuitBreakerOpen": false,
    "errorPercentage": 0,
    "errorCount": 0,
    "requestCount": 0,
    "rollingCountBadRequests": 0,
    "rollingCountCollapsedRequests": 0,
    "rollingCountEmit": 0,
    "rollingCountExceptionsThrown": 0,
    "rollingCountFailure": 0,
    "rollingCountFallbackEmit": 0,
    "rollingCountFallbackFailure": 0,
    "rollingCountFallbackMissing": 0,
    "rollingCountFallbackRejection": 0,
    "rollingCountFallbackSuccess": 0,
    "rollingCountResponsesFromCache": 0,
    "rollingCountSemaphoreRejected": 0,
    "rollingCountShortCircuited": 0,
    "rollingCountSuccess": 0,
    "rollingCountThreadPoolRejected": 0,
    "rollingCountTimeout": 0,
    "currentConcurrentExecutionCount": 0,
    "rollingMaxConcurrentExecutionCount": 0,
    "latencyExecute_mean": 0,
    "latencyExecute": {
      "0": 0,
      "25": 0,
      "50": 0,
      "75": 0,
      "90": 0,
      "95": 0,
      "99": 0,
      "100": 0,
      "99.5": 0
    },
    "latencyTotal_mean": 0,
    "latencyTotal": {
      "0": 0,
      "25": 0,
      "50": 0,
      "75": 0,
      "90": 0,
      "95": 0,
      "99": 0,
      "100": 0,
      "99.5": 0
    },
    "propertyValue_circuitBreakerRequestVolumeThreshold": 20,
    "propertyValue_circuitBreakerSleepWindowInMilliseconds": 5000,
    "propertyValue_circuitBreakerErrorThresholdPercentage": 50,
    "propertyValue_circuitBreakerForceOpen": false,
    "propertyValue_circuitBreakerForceClosed": false,
    "propertyValue_circuitBreakerEnabled": true,
    "propertyValue_executionIsolationStrategy": "THREAD",
    "propertyValue_executionIsolationThreadTimeoutInMilliseconds": 1500,
    "propertyValue_executionTimeoutInMilliseconds": 1500,
    "propertyValue_executionIsolationThreadInterruptOnTimeout": true,
    "propertyValue_executionIsolationThreadPoolKeyOverride": null,
    "propertyValue_executionIsolationSemaphoreMaxConcurrentRequests": 10,
    "propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests": 10,
    "propertyValue_metricsRollingStatisticalWindowInMilliseconds": 10000,
    "propertyValue_requestCacheEnabled": true,
    "propertyValue_requestLogEnabled": true,
    "reportingHosts": 1
}
```

HystrixThreadPool instances will emit data such as this:

```json
data:
{
  "type": "HystrixThreadPool",
  "name": "remote",
  "currentTime": 1458251617773,
  "currentActiveCount": 0,
  "currentCompletedTaskCount": 6,
  "currentCorePoolSize": 10,
  "currentLargestPoolSize": 6,
  "currentMaximumPoolSize": 10,
  "currentPoolSize": 6,
  "currentQueueSize": 0,
  "currentTaskCount": 6,
  "rollingCountThreadsExecuted": 0,
  "rollingMaxActiveThreads": 0,
  "rollingCountCommandsRejected": 0,
  "propertyValue_queueSizeRejectionThreshold": 5,
  "propertyValue_metricsRollingStatisticalWindowInMilliseconds": 10000,
  "reportingHosts": 1
}
```

# Setup

First include `hystrix-vertx-metrics-stream-*.jar` in your classpath.
Then you have some ways for deploying.

1) Don't know anything about Vertx? No problem!
Use the helper class `EventMetricsStreamHelper` and the method `deployStandaloneMetricsStream`.
It will create a and setup the environment, making the metrics available at `host:8099/hystrix.stream` 
```
EventMetricsStreamHelper.deployStandaloneMetricsStream();
```

2) Do you have a Vertx app using vertx-web? Install a handler on your router!

```java
Router router = // your router already instantiated for http server
router.route(EventMetricsStreamHandler.DEFAULT_HYSTRIX_PREFIX) // or in what path you want or use the dynamicProperty hystrix.vertx.stream.httpServer.path with archaius
      .method(HttpMethod.GET)
      .handler(EventMetricsStreamHandler.createHandler());
```

3) Deploy a EventMetricsStreamVerticle in a Vertx application with full classified class name or programmatically created instance: 
Just use a vert-instance that you wanna, example:

```java
Vertx vertx = //... your instance

// deploy using full qualified class name
vertx.deployVerticle("com.netflix.hystrix.contrib.vertx.metricsstream.EventMetricsStreamVerticle",
                     result -> {
                       if (result.succed) {
                        // success!
                       } else {
                        // fail :(
                       }
                     });
                     
// deploy programatically instantiating the verticle
vertx.deployVerticle(new EventMetricsStreamVerticle(), result -> {
                       if (result.succed) {
                        // success!
                       } else {
                        // fail :(
                       }
                     });
```

4) Run a standalone instance with customized threads.

In Vertx you can customize how many event loop threads Vertx will spawn, when you deploy a verticle, the vertx will bind one event loop thread with this verticle, so, if you wanna use only one thread to handle all of the connections, you do the following:

`Note on this example, i did not benchmark it, but usually one thread can handle alot of concurrency, so if you are not in a vertx app and just wanna use this vertx verticle to publish metrics for you, try configuring only one thread for the event loop and only one instance for the verticle.`

```java
final int eventLoopThreads = 1; // total of threads for the event loop pool

// Create a vertx instance with the configurated event loop thread
final Vertx vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(eventLoopThreads));

// Creates a configuration for deployment, where we will use 1 verticle instance
final DeploymentOptions deploymentOptions = new DeploymentOptions().setInstances(1);

// Deploy our verticle with the configurated deployment settings in the 
// configurated vertx instance
vertx.deployVerticle("com.netflix.hystrix.contrib.vertx.metricsstream.EventMetricsStreamVerticle",
                     deploymentOptions,
                     result -> {
                       if (result.succed) {
                        // success!
                       } else {
                        // fail :(
                       }
                     });
```

`Important Note: The Verticles instance count is not restricted to 1 instance per event loop thread, you can have as many as you want per event loop thread, but for this use case i don't think will be necessary more than one` 

# Configurations
There are some configuration you can set using Archaius.

| Configuration | Default | Description |
| ------------- | ------- | ----------- |
| `hystrix.stream.maxConcurrentConnections` | `5` | Maximum concurrent connections, if you exceed it, you will receive a 503 error. |
| `hystrix.vertx.stream.httpServer.port` | `8099` | Port which the Http Server should bind. |
| `hystrix.vertx.stream.httpServer.path` | `hystrix.stream` | Path for acessing the event stream. |

The interval / delay from the dashboard plugin is also available, you can pass it as a query parameter delay=[time-in-millis]

# Test

To test your installation you can use curl like this:

```
$ curl http://hostname:port/hystrix.stream

data: {"rollingCountFailure":0,"propertyValue_executionIsolationThreadInterruptOnTimeout":true,"rollingCountTimeout":0,"rollingCountExceptionsThrown":0,"rollingCountFallbackSuccess":0,"errorCount":0,"type":"HystrixCommand","propertyValue_circuitBreakerEnabled":true,"reportingHosts":1,"latencyTotal":{"0":0,"95":0,"99.5":0,"90":0,"25":0,"99":0,"75":0,"100":0,"50":0},"currentConcurrentExecutionCount":0,"rollingCountSemaphoreRejected":0,"rollingCountFallbackRejection":0,"rollingCountShortCircuited":0,"rollingCountResponsesFromCache":0,"propertyValue_circuitBreakerForceClosed":false,"name":"IdentityCookieAuthSwitchProfile","propertyValue_executionIsolationThreadPoolKeyOverride":"null","rollingCountSuccess":0,"propertyValue_requestLogEnabled":true,"requestCount":0,"rollingCountCollapsedRequests":0,"errorPercentage":0,"propertyValue_circuitBreakerSleepWindowInMilliseconds":5000,"latencyTotal_mean":0,"propertyValue_circuitBreakerForceOpen":false,"propertyValue_circuitBreakerRequestVolumeThreshold":20,"propertyValue_circuitBreakerErrorThresholdPercentage":50,"propertyValue_executionIsolationStrategy":"THREAD","rollingCountFallbackFailure":0,"isCircuitBreakerOpen":false,"propertyValue_executionIsolationSemaphoreMaxConcurrentRequests":20,"propertyValue_executionIsolationThreadTimeoutInMilliseconds":1000,"propertyValue_metricsRollingStatisticalWindowInMilliseconds":10000,"propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests":10,"latencyExecute":{"0":0,"95":0,"99.5":0,"90":0,"25":0,"99":0,"75":0,"100":0,"50":0},"group":"IDENTITY","latencyExecute_mean":0,"propertyValue_requestCacheEnabled":true,"rollingCountThreadPoolRejected":0}

data: {"rollingCountFailure":0,"propertyValue_executionIsolationThreadInterruptOnTimeout":true,"rollingCountTimeout":0,"rollingCountExceptionsThrown":0,"rollingCountFallbackSuccess":0,"errorCount":0,"type":"HystrixCommand","propertyValue_circuitBreakerEnabled":true,"reportingHosts":3,"latencyTotal":{"0":1,"95":1,"99.5":1,"90":1,"25":1,"99":1,"75":1,"100":1,"50":1},"currentConcurrentExecutionCount":0,"rollingCountSemaphoreRejected":0,"rollingCountFallbackRejection":0,"rollingCountShortCircuited":0,"rollingCountResponsesFromCache":0,"propertyValue_circuitBreakerForceClosed":false,"name":"CryptexDecrypt","propertyValue_executionIsolationThreadPoolKeyOverride":"null","rollingCountSuccess":1,"propertyValue_requestLogEnabled":true,"requestCount":1,"rollingCountCollapsedRequests":0,"errorPercentage":0,"propertyValue_circuitBreakerSleepWindowInMilliseconds":15000,"latencyTotal_mean":1,"propertyValue_circuitBreakerForceOpen":false,"propertyValue_circuitBreakerRequestVolumeThreshold":60,"propertyValue_circuitBreakerErrorThresholdPercentage":150,"propertyValue_executionIsolationStrategy":"THREAD","rollingCountFallbackFailure":0,"isCircuitBreakerOpen":false,"propertyValue_executionIsolationSemaphoreMaxConcurrentRequests":60,"propertyValue_executionIsolationThreadTimeoutInMilliseconds":3000,"propertyValue_metricsRollingStatisticalWindowInMilliseconds":30000,"propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests":30,"latencyExecute":{"0":0,"95":0,"99.5":0,"90":0,"25":0,"99":0,"75":0,"100":0,"50":0},"group":"CRYPTEX","latencyExecute_mean":0,"propertyValue_requestCacheEnabled":true,"rollingCountThreadPoolRejected":0}
```