# hystrix-metrics-event-stream

This module exposes metrics in a [text/event-stream](https://developer.mozilla.org/en-US/docs/Server-sent_events/Using_server-sent_events) formatted stream that continues as long as a client holds the connection.

Each HystrixCommand instance will emit data such as this (without line breaks):

```json
data: {
  "type": "HystrixCommand",
  "name": "PlaylistGet",
  "group": "PlaylistGet",
  "currentTime": 1355239617628,
  "isCircuitBreakerOpen": false,
  "errorPercentage": 0,
  "errorCount": 0,
  "requestCount": 121,
  "rollingCountCollapsedRequests": 0,
  "rollingCountExceptionsThrown": 0,
  "rollingCountFailure": 0,
  "rollingCountFallbackFailure": 0,
  "rollingCountFallbackRejection": 0,
  "rollingCountFallbackSuccess": 0,
  "rollingCountResponsesFromCache": 69,
  "rollingCountSemaphoreRejected": 0,
  "rollingCountShortCircuited": 0,
  "rollingCountSuccess": 121,
  "rollingCountThreadPoolRejected": 0,
  "rollingCountTimeout": 0,
  "currentConcurrentExecutionCount": 0,
  "latencyExecute_mean": 13,
  "latencyExecute": {
    "0": 3,
    "25": 6,
    "50": 8,
    "75": 14,
    "90": 26,
    "95": 37,
    "99": 75,
    "99.5": 92,
    "100": 252
  },
  "latencyTotal_mean": 15,
  "latencyTotal": {
    "0": 3,
    "25": 7,
    "50": 10,
    "75": 18,
    "90": 32,
    "95": 43,
    "99": 88,
    "99.5": 160,
    "100": 253
  },
  "propertyValue_circuitBreakerRequestVolumeThreshold": 20,
  "propertyValue_circuitBreakerSleepWindowInMilliseconds": 5000,
  "propertyValue_circuitBreakerErrorThresholdPercentage": 50,
  "propertyValue_circuitBreakerForceOpen": false,
  "propertyValue_circuitBreakerForceClosed": false,
  "propertyValue_circuitBreakerEnabled": true,
  "propertyValue_executionIsolationStrategy": "THREAD",
  "propertyValue_executionIsolationThreadTimeoutInMilliseconds": 800,
  "propertyValue_executionIsolationThreadInterruptOnTimeout": true,
  "propertyValue_executionIsolationThreadPoolKeyOverride": null,
  "propertyValue_executionIsolationSemaphoreMaxConcurrentRequests": 20,
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
  "currentPoolSize": 30,
  "rollingMaxActiveThreads": 13,
  "currentActiveCount": 0,
  "currentCompletedTaskCount": 4459519,
  "propertyValue_queueSizeRejectionThreshold": 30,
  "type": "HystrixThreadPool",
  "reportingHosts": 3,
  "propertyValue_metricsRollingStatisticalWindowInMilliseconds": 30000,
  "name": "ABClient",
  "currentLargestPoolSize": 30,
  "currentCorePoolSize": 30,
  "currentQueueSize": 0,
  "currentTaskCount": 4459519,
  "rollingCountThreadsExecuted": 919,
  "currentMaximumPoolSize": 30
}
```

# Binaries

Binaries and dependency information for Maven, Ivy, Gradle and others can be found at [http://search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22hystrix-metrics-event-stream%22).

Example for Maven:

```xml
<dependency>
    <groupId>com.netflix.hystrix</groupId>
    <artifactId>hystrix-metrics-event-stream</artifactId>
    <version>1.1.1</version>
</dependency>
```
and for Ivy:

```xml
<dependency org="com.netflix.hystrix" name="hystrix-metrics-event-stream" rev="1.1.0" />
```

# Installation

1) Include hystrix-metrics-event-stream-*.jar in your classpath (such as /WEB-INF/lib)  
2) Add the following to your application web.xml:  

```xml
  <servlet>
		<description></description>
		<display-name>HystrixMetricsStreamServlet</display-name>
		<servlet-name>HystrixMetricsStreamServlet</servlet-name>
		<servlet-class>com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsStreamServlet</servlet-class>
	</servlet>

	<servlet-mapping>
		<servlet-name>HystrixMetricsStreamServlet</servlet-name>
		<url-pattern>/hystrix.stream</url-pattern>
	</servlet-mapping>
```
