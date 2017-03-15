# hystrix-metrics-event-stream-jaxrs

This module is a JAX-RS implementation of [hystrix-metrics-event-stream](https://github.com/Netflix/Hystrix/tree/master/hystrix-contrib/hystrix-metrics-event-stream) module without any Servlet API dependency and exposes metrics in a [text/event-stream](https://developer.mozilla.org/en-US/docs/Server-sent_events/Using_server-sent_events) formatted stream that continues as long as a client holds the connection.


# Binaries

Binaries and dependency information for Maven, Ivy, Gradle and others can be found at [http://search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22hystrix-metrics-event-stream-jaxrs%22).

Example for Maven ([lookup latest version](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22hystrix-metrics-event-stream-jaxrs%22)):

```xml
<dependency>
    <groupId>com.netflix.hystrix</groupId>
    <artifactId>hystrix-metrics-event-stream-jaxrs</artifactId>
    <version>1.6.0</version>
</dependency>
```
and for Ivy:

```xml
<dependency org="com.netflix.hystrix" name="hystrix-metrics-event-stream-jaxrs" rev="1.6.0" />
```

# Installation

1) Include hystrix-metrics-event-stream-jaxrs*.jar in your classpath (such as /WEB-INF/lib). 
2) Register `HystrixStreamFeature` in your `javax.ws.rs.core.Application` as shown below.

```java

public class HystrixStreamApplication extends Application{

    @Override
    public Set<Class<?>> getClasses() {
			Set<Class<?>> clazzes = new HashSet<Class<?>>();
			clazzes.add(HystrixStreamFeature.class);
			return clazzes;
   }
}
```

3) Following end-points are available
  * /hystrix.stream - Stream Hystrix Metrics
  * /hystrix/utilization.stream - Stream Hystrix Utilization
  * /hystrix/config.stream - Stream Hystrix configuration
  * /hystrix/request.stream - Stream Hystrix SSE events
  


# Test

To test your installation you can use curl like this:

```
$ curl http://hostname:port/appname/hystrix.stream

data: {"rollingCountFailure":0,"propertyValue_executionIsolationThreadInterruptOnTimeout":true,"rollingCountTimeout":0,"rollingCountExceptionsThrown":0,"rollingCountFallbackSuccess":0,"errorCount":0,"type":"HystrixCommand","propertyValue_circuitBreakerEnabled":true,"reportingHosts":1,"latencyTotal":{"0":0,"95":0,"99.5":0,"90":0,"25":0,"99":0,"75":0,"100":0,"50":0},"currentConcurrentExecutionCount":0,"rollingCountSemaphoreRejected":0,"rollingCountFallbackRejection":0,"rollingCountShortCircuited":0,"rollingCountResponsesFromCache":0,"propertyValue_circuitBreakerForceClosed":false,"name":"IdentityCookieAuthSwitchProfile","propertyValue_executionIsolationThreadPoolKeyOverride":"null","rollingCountSuccess":0,"propertyValue_requestLogEnabled":true,"requestCount":0,"rollingCountCollapsedRequests":0,"errorPercentage":0,"propertyValue_circuitBreakerSleepWindowInMilliseconds":5000,"latencyTotal_mean":0,"propertyValue_circuitBreakerForceOpen":false,"propertyValue_circuitBreakerRequestVolumeThreshold":20,"propertyValue_circuitBreakerErrorThresholdPercentage":50,"propertyValue_executionIsolationStrategy":"THREAD","rollingCountFallbackFailure":0,"isCircuitBreakerOpen":false,"propertyValue_executionIsolationSemaphoreMaxConcurrentRequests":20,"propertyValue_executionIsolationThreadTimeoutInMilliseconds":1000,"propertyValue_metricsRollingStatisticalWindowInMilliseconds":10000,"propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests":10,"latencyExecute":{"0":0,"95":0,"99.5":0,"90":0,"25":0,"99":0,"75":0,"100":0,"50":0},"group":"IDENTITY","latencyExecute_mean":0,"propertyValue_requestCacheEnabled":true,"rollingCountThreadPoolRejected":0}

data: {"rollingCountFailure":0,"propertyValue_executionIsolationThreadInterruptOnTimeout":true,"rollingCountTimeout":0,"rollingCountExceptionsThrown":0,"rollingCountFallbackSuccess":0,"errorCount":0,"type":"HystrixCommand","propertyValue_circuitBreakerEnabled":true,"reportingHosts":3,"latencyTotal":{"0":1,"95":1,"99.5":1,"90":1,"25":1,"99":1,"75":1,"100":1,"50":1},"currentConcurrentExecutionCount":0,"rollingCountSemaphoreRejected":0,"rollingCountFallbackRejection":0,"rollingCountShortCircuited":0,"rollingCountResponsesFromCache":0,"propertyValue_circuitBreakerForceClosed":false,"name":"CryptexDecrypt","propertyValue_executionIsolationThreadPoolKeyOverride":"null","rollingCountSuccess":1,"propertyValue_requestLogEnabled":true,"requestCount":1,"rollingCountCollapsedRequests":0,"errorPercentage":0,"propertyValue_circuitBreakerSleepWindowInMilliseconds":15000,"latencyTotal_mean":1,"propertyValue_circuitBreakerForceOpen":false,"propertyValue_circuitBreakerRequestVolumeThreshold":60,"propertyValue_circuitBreakerErrorThresholdPercentage":150,"propertyValue_executionIsolationStrategy":"THREAD","rollingCountFallbackFailure":0,"isCircuitBreakerOpen":false,"propertyValue_executionIsolationSemaphoreMaxConcurrentRequests":60,"propertyValue_executionIsolationThreadTimeoutInMilliseconds":3000,"propertyValue_metricsRollingStatisticalWindowInMilliseconds":30000,"propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests":30,"latencyExecute":{"0":0,"95":0,"99.5":0,"90":0,"25":0,"99":0,"75":0,"100":0,"50":0},"group":"CRYPTEX","latencyExecute_mean":0,"propertyValue_requestCacheEnabled":true,"rollingCountThreadPoolRejected":0}
```

