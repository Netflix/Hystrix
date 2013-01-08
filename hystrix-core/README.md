## hystrix-core

This is the core module of Hystrix.

Key classes you're most likely to interact with are:

- HystrixCommand [Source](./hystrix-core/src/main/java/com/netflix/hystrix/HystrixCommand.java) [Javadoc](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/HystrixCommand.html)
- HystrixCollapser [Source](./hystrix-core/src/main/java/com/netflix/hystrix/HystrixCollapser.java) [Javadoc](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/HystrixCollapser.html)
- HystrixRequestLog [Source](./hystrix-core/src/main/java/com/netflix/hystrix/HystrixRequestLog.java) [Javadoc](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/HystrixRequestLog.html)
- HystrixPlugins [Source](./hystrix-core/src/main/java/com/netflix/hystrix/strategy/HystrixPlugins.java) [Javadoc](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/strategy/HystrixPlugins.html)
- HystrixRequestContext [Source](./hystrix-core/src/main/java/com/netflix/hystrix/strategy/concurrency/HystrixRequestContext.java) [Javadoc](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/strategy/concurrency/HystrixRequestContext.html)

## Documentation

A general project README can be found at the [project home](../../).

The [Wiki](../../wiki) contains detailed documentation.


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
