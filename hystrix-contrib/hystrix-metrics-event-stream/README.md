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

Example for Maven ([lookup latest version](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22hystrix-metrics-event-stream%22)):

```xml
<dependency>
    <groupId>com.netflix.hystrix</groupId>
    <artifactId>hystrix-metrics-event-stream</artifactId>
    <version>1.4.10</version>
</dependency>
```
and for Ivy:

```xml
<dependency org="com.netflix.hystrix" name="hystrix-metrics-event-stream" rev="1.4.10" />
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

# Test

To test your installation you can use curl like this:

```
$ curl http://hostname:port/appname/hystrix.stream

data: {"rollingCountFailure":0,"propertyValue_executionIsolationThreadInterruptOnTimeout":true,"rollingCountTimeout":0,"rollingCountExceptionsThrown":0,"rollingCountFallbackSuccess":0,"errorCount":0,"type":"HystrixCommand","propertyValue_circuitBreakerEnabled":true,"reportingHosts":1,"latencyTotal":{"0":0,"95":0,"99.5":0,"90":0,"25":0,"99":0,"75":0,"100":0,"50":0},"currentConcurrentExecutionCount":0,"rollingCountSemaphoreRejected":0,"rollingCountFallbackRejection":0,"rollingCountShortCircuited":0,"rollingCountResponsesFromCache":0,"propertyValue_circuitBreakerForceClosed":false,"name":"IdentityCookieAuthSwitchProfile","propertyValue_executionIsolationThreadPoolKeyOverride":"null","rollingCountSuccess":0,"propertyValue_requestLogEnabled":true,"requestCount":0,"rollingCountCollapsedRequests":0,"errorPercentage":0,"propertyValue_circuitBreakerSleepWindowInMilliseconds":5000,"latencyTotal_mean":0,"propertyValue_circuitBreakerForceOpen":false,"propertyValue_circuitBreakerRequestVolumeThreshold":20,"propertyValue_circuitBreakerErrorThresholdPercentage":50,"propertyValue_executionIsolationStrategy":"THREAD","rollingCountFallbackFailure":0,"isCircuitBreakerOpen":false,"propertyValue_executionIsolationSemaphoreMaxConcurrentRequests":20,"propertyValue_executionIsolationThreadTimeoutInMilliseconds":1000,"propertyValue_metricsRollingStatisticalWindowInMilliseconds":10000,"propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests":10,"latencyExecute":{"0":0,"95":0,"99.5":0,"90":0,"25":0,"99":0,"75":0,"100":0,"50":0},"group":"IDENTITY","latencyExecute_mean":0,"propertyValue_requestCacheEnabled":true,"rollingCountThreadPoolRejected":0}

data: {"rollingCountFailure":0,"propertyValue_executionIsolationThreadInterruptOnTimeout":true,"rollingCountTimeout":0,"rollingCountExceptionsThrown":0,"rollingCountFallbackSuccess":0,"errorCount":0,"type":"HystrixCommand","propertyValue_circuitBreakerEnabled":true,"reportingHosts":3,"latencyTotal":{"0":1,"95":1,"99.5":1,"90":1,"25":1,"99":1,"75":1,"100":1,"50":1},"currentConcurrentExecutionCount":0,"rollingCountSemaphoreRejected":0,"rollingCountFallbackRejection":0,"rollingCountShortCircuited":0,"rollingCountResponsesFromCache":0,"propertyValue_circuitBreakerForceClosed":false,"name":"CryptexDecrypt","propertyValue_executionIsolationThreadPoolKeyOverride":"null","rollingCountSuccess":1,"propertyValue_requestLogEnabled":true,"requestCount":1,"rollingCountCollapsedRequests":0,"errorPercentage":0,"propertyValue_circuitBreakerSleepWindowInMilliseconds":15000,"latencyTotal_mean":1,"propertyValue_circuitBreakerForceOpen":false,"propertyValue_circuitBreakerRequestVolumeThreshold":60,"propertyValue_circuitBreakerErrorThresholdPercentage":150,"propertyValue_executionIsolationStrategy":"THREAD","rollingCountFallbackFailure":0,"isCircuitBreakerOpen":false,"propertyValue_executionIsolationSemaphoreMaxConcurrentRequests":60,"propertyValue_executionIsolationThreadTimeoutInMilliseconds":3000,"propertyValue_metricsRollingStatisticalWindowInMilliseconds":30000,"propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests":30,"latencyExecute":{"0":0,"95":0,"99.5":0,"90":0,"25":0,"99":0,"75":0,"100":0,"50":0},"group":"CRYPTEX","latencyExecute_mean":0,"propertyValue_requestCacheEnabled":true,"rollingCountThreadPoolRejected":0}
```

# Clojure Version

A Clojure version of this module can be found at https://github.com/josephwilk/hystrix-event-stream-clj
