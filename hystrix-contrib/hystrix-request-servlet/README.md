## Hystrix Request Servlet Filters

This module contains functional examples for a J2EE/Servlet environment that initialize and use the [HystrixRequestContext](../../hystrix-core/src/main/java/com/netflix/hystrix/strategy/concurrency/HystrixRequestContext.java).

You can use this module as is or implement your own as it is very simple what these classes do.

If using a framework that doesn't use Servlets, or a framework with other lifecycle hooks you may need to implement your own anyways.

## [HystrixRequestContextServletFilter](src/main/java/com/netflix/hystrix/contrib/requestservlet/HystrixRequestContextServletFilter.java)

This initializes the [HystrixRequestContext](../../hystrix-core/src/main/java/com/netflix/hystrix/strategy/concurrency/HystrixRequestContext.java) at the beginning of each HTTP request and then cleans it up at the end.

You install it by adding the following to your web.xml:

```xml
  <filter>
    <display-name>HystrixRequestContextServletFilter</display-name>
    <filter-name>HystrixRequestContextServletFilter</filter-name>
    <filter-class>com.netflix.hystrix.contrib.requestservlet.HystrixRequestContextServletFilter</filter-class>
  </filter>
  <filter-mapping>
    <filter-name>HystrixRequestContextServletFilter</filter-name>
    <url-pattern>/*</url-pattern>
  </filter-mapping>
```

### [HystrixRequestLogViaLoggerServletFilter](src/main/java/com/netflix/hystrix/contrib/requestservlet/HystrixRequestLogViaLoggerServletFilter.java)

This logs an INFO message with the output from [HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString()](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/HystrixRequestLog.html#getExecutedCommandsAsString(\)) at the end of each requet.

You install it by adding the following to your web.xml:

```xml
  <filter>
    <display-name>HystrixRequestLogViaLoggerServletFilter</display-name>
    <filter-name>HystrixRequestLogViaLoggerServletFilter</filter-name>
    <filter-class>com.netflix.hystrix.contrib.requestservlet.HystrixRequestLogViaLoggerServletFilter</filter-class>
  </filter>
  <filter-mapping>
    <filter-name>HystrixRequestLogViaLoggerServletFilter</filter-name>
    <url-pattern>/*</url-pattern>
  </filter-mapping>
```


### [HystrixRequestLogViaResponseHeaderServletFilter](src/main/java/com/netflix/hystrix/contrib/requestservlet/HystrixRequestLogViaResponseHeaderServletFilter.java)

This adds the output of [HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString()](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/HystrixRequestLog.html#getExecutedCommandsAsString(\)) to the HTTP response as header "X-HystrixLog".

Note that this will not work if the response has been flushed already (such as on a progressively rendered page).

```xml
  <filter>
    <display-name>HystrixRequestLogViaResponseHeaderServletFilter</display-name>
    <filter-name>HystrixRequestLogViaResponseHeaderServletFilter</filter-name>
    <filter-class>com.netflix.hystrix.contrib.requestservlet.HystrixRequestLogViaResponseHeaderServletFilter</filter-class>
  </filter>
  <filter-mapping>
    <filter-name>HystrixRequestLogViaResponseHeaderServletFilter</filter-name>
    <url-pattern>/*</url-pattern>
  </filter-mapping>
```

