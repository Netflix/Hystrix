## hystrix-core

This is the core module of Hystrix.

Key classes you're most likely to interact with are:

- HystrixCommand [Source](https://github.com/Netflix/Hystrix/tree/master/hystrix-core/src/main/java/com/netflix/hystrix/HystrixCommand.java) [Javadoc](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/HystrixCommand.html)
- HystrixObservableCommand [Source](https://github.com/Netflix/Hystrix/tree/master/hystrix-core/src/main/java/com/netflix/hystrix/HystrixObservableCommand.java) [Javadoc](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/HystrixObservableCommand.html)
- HystrixCollapser [Source](https://github.com/Netflix/Hystrix/tree/master/hystrix-core/src/main/java/com/netflix/hystrix/HystrixCollapser.java) [Javadoc](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/HystrixCollapser.html)
- HystrixRequestLog [Source](https://github.com/Netflix/Hystrix/tree/master/hystrix-core/src/main/java/com/netflix/hystrix/HystrixRequestLog.java) [Javadoc](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/HystrixRequestLog.html)
- HystrixPlugins [Source](https://github.com/Netflix/Hystrix/tree/master/hystrix-core/src/main/java/com/netflix/hystrix/strategy/HystrixPlugins.java) [Javadoc](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/strategy/HystrixPlugins.html)
- HystrixRequestContext [Source](https://github.com/Netflix/Hystrix/tree/master/hystrix-core/src/main/java/com/netflix/hystrix/strategy/concurrency/HystrixRequestContext.java) [Javadoc](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/strategy/concurrency/HystrixRequestContext.html)

## Documentation

A general project README can be found at the [project home](https://github.com/Netflix/Hystrix).

The [Wiki](https://github.com/Netflix/Hystrix/wiki) contains detailed documentation.


## Maven Central

Binaries and dependencies for this module can be found on [http://search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20a%3A%22hystrix-core%22).

__GroupId: com.netflix.hystrix__  
__ArtifactId: hystrix-core__  

Example for Maven:

```xml
<dependency>
    <groupId>com.netflix.hystrix</groupId>
    <artifactId>hystrix-core</artifactId>
    <version>1.2.0</version>
</dependency>
```
and for Ivy:

```xml
<dependency org="com.netflix.hystrix" name="hystrix-core" rev="1.2.0" />
```
