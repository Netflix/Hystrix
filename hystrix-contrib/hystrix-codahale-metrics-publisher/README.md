# hystrix-codahale-metrics-publisher

This is an implementation of [HystrixMetricsPublisher](http://netflix.github.com/Hystrix/javadoc/index.html?com/netflix/hystrix/strategy/metrics/HystrixMetricsPublisher.html) that publishes metrics using [Coda Hale Metrics](http://metrics.codahale.com) version 3. If you are using Yammer Metrics version 2, please use the [hystrix-yammer-metrics-publisher](../hystrix-yammer-metrics-publisher) module instead.

See the [Metrics & Monitoring](https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring) Wiki for more information.

# Binaries

Binaries and dependency information for Maven, Ivy, Gradle and others can be found at [http://search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22hystrix-codahale-metrics-publisher%22).

Example for Maven:

```xml
<dependency>
    <groupId>com.netflix.hystrix</groupId>
    <artifactId>hystrix-codahale-metrics-publisher</artifactId>
    <version>1.1.2</version>
</dependency>
```

and for Ivy:

```xml
<dependency org="com.netflix.hystrix" name="hystrix-codahale-metrics-publisher" rev="1.1.2" />
```

Example usage (make it work/plug it in):

      HystrixPlugins.getInstance().registerMetricsPublisher(new HystrixCodahaleMetricsPublisher(yourMetricRegistry));
