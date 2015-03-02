# hystrix-servo-metrics-publisher

This is an implementation of [HystrixMetricsPublisher](http://netflix.github.com/Hystrix/javadoc/index.html?com/netflix/hystrix/strategy/metrics/HystrixMetricsPublisher.html) that publishes metrics using [Netflix Servo](https://github.com/Netflix/servo).

Servo enables metrics to be exposed via JMX or published to Graphite or CloudWatch.

See the [Metrics & Monitoring](https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring) Wiki for more information.

# Binaries

Binaries and dependency information for Maven, Ivy, Gradle and others can be found at [http://search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22hystrix-servo-metrics-publisher%22).

Example for Maven:

```xml
<dependency>
    <groupId>com.netflix.hystrix</groupId>
    <artifactId>hystrix-servo-metrics-publisher</artifactId>
    <version>1.1.2</version>
</dependency>
```

and for Ivy:

```xml
<dependency org="com.netflix.hystrix" name="hystrix-servo-metrics-publisher" rev="1.1.2" />
```

#Example Publishing metrics to Graphite

Some minimal Servo configuration must be done during application startup (ie time of Hystrix plugin registration) to enable Servo to publish Hystrix metrics to Graphite:

```java
// Registry plugin with hystrix
HystrixPlugins.getInstance().registerMetricsPublisher(HystrixServoMetricsPublisher.getInstance());
        
   ........
        
// Minimal Servo configuration for publishing to Graphite
final List<MetricObserver> observers = new ArrayList<MetricObserver>();

observers.add(new GraphiteMetricObserver(getHostName(), "graphite-server.example.com:2003"));
PollScheduler.getInstance().start();
PollRunnable task = new PollRunnable(new MonitorRegistryMetricPoller(), BasicMetricFilter.MATCH_ALL, true, observers);
PollScheduler.getInstance().addPoller(task, 5, TimeUnit.SECONDS);
```

It's that simple.  See [Servo wiki](https://github.com/Netflix/servo/wiki/Getting-Started) and [Publishing to Graphite](https://github.com/Netflix/servo/wiki/Publishing-to-Graphite) for full documentation.
