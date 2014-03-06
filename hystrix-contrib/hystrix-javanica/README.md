# hystrix-javanica

Java language has a great advantages over other languages such as reflection and annotations.
All modern frameworks such as Spring, Hibernate, myBatis and etc. seek to use this advantages to the maximum.
The idea of introduction annotations in Hystrix is obvious solution for improvement. Currently using Hystrix involves writing a lot of code that is a barrier to rapid development. You likely be spending a lot of time on writing a Hystrix commands. Idea of the Javanica project is make easier using of Hystrix by the introduction of support annotations.

First of all in order to use hystrix-javanica you need to add hystrix-javanica dependency in your project.

Example for Maven:
```xml
<dependency>
    <groupId>com.netflix.hystrix</groupId>
    <artifactId>hystrix-javanica</artifactId>
    <version>x.y.z</version>
</dependency>
```

To implement AOP functionality in the project was used AspectJ library. If in your project already used AspectJ then you need to add hystrix aspect in aop.xml as below:
```xml
<aspects>
        ...
        <aspect name="com.netflix.hystrix.contrib.javanica.aop.aspectj.HystrixAspect"/>
        ...
</aspects>
```
More about AspectJ configuration read [here] (http://www.eclipse.org/aspectj/doc/next/devguide/ltw-configuration.html)


If you use Spring AOP in your project then you need to add specific configuration using Spring aop namespace in order to make Spring capable to manage aspects which were written using AspectJ and declare HystrixAspect as Spring bean like below:

```xml
    <aop:aspectj-autoproxy/>
    <bean id="hystrixAspect" class="com.netflix.hystrix.contrib.javanica.aop.aspectj.HystrixAspect"></bean>
```

It doesn't matter which approach you use to create proxies in Spring, javanica works fine with JDK and CGLIB proxies. If you use another framework for aop which supports AspectJ and uses other libs (Javassist for instance) to create proxies then let us know what lib you use to create proxies and we'll try to add support for this library in near future.

More about Spring AOP + AspectJ read [here] (http://docs.spring.io/spring/docs/3.2.x/spring-framework-reference/html/aop.html)

# How to use

## Hystrix command

To run method as Hystrix command you need to annotate method with @HystrixCommand annotation, for example
```java
    @HystrixCommand(fallbackMethod = "defaultUser")
    public User getUserById(String id) {
        return userResource.getUserById(id);
    }

    private User defaultUser(String id) {
        return new User();
    }
```
In example above the 'getUserById' method will be processed [synchronously](https://github.com/Netflix/Hystrix/wiki/How-To-Use#wiki-Synchronous-Execution) as Hystrix command. Fallback method can be private or public. Method 'defaultUser' will be used to process fallback logic in a case of any errors. If you need to run 'defaultUser' as command then you can annotate it with HystrixCommand annotation as below:
```java
    @HystrixCommand(fallbackMethod = "defaultUser")
    public User getUserById(String id) {
        return userResource.getUserById(id);
    }

    @HystrixCommand
    private User defaultUser(String id) {
        return new User();
    }
```

If fallback method was marked with @HystrixCommand then this method also can has own fallback method, as in the example below:
```java
    @HystrixCommand(fallbackMethod = "defaultUser")
    public User getUserById(String id) {
        return userResource.getUserById(id);
    }

    @HystrixCommand(fallbackMethod = "defaultUserSecond")
    private User defaultUser(String id) {
        return new User();
    }
    
    @HystrixCommand
    private User defaultUserSecond(String id) {
        return new User("def", "def");
    }
```

Its important to remember that Hystrix command and fallback should be placed in the same class.

To process Hystrix command asynchronously you should return an instance of AsyncCommand in your command method as in the exapmple below:
```java
    @HystrixCommand
    public Future<User> getUserByIdAsync(final String id) {
        return new AsyncCommand<User>() {
            @Override
            public User invoke() {
                return userResource.getUserById(id);
            }
        };
    }
```

The return type of command method should be Future that indicates that a command should be executed [asynchronously] (https://github.com/Netflix/Hystrix/wiki/How-To-Use#wiki-Asynchronous-Execution).

Command properties can be set using commandProperties field like below:
```java

    @HystrixCommand(fallbackMethod = "defaultUser", 
        commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "500")
        })
    public User getUserById(String id) {
        return userResource.getUserById(id);
    }
```


Javanica sets command properties using Hystrix ConfigurationManager.
For the example above Javanica behind the scenes performs next action:
```java
ConfigurationManager.getConfigInstance().setProperty("hystrix.command.getUserById.execution.isolation.thread.timeoutInMilliseconds", "500");
```

More about Hystrix command properties [command](https://github.com/Netflix/Hystrix/wiki/Configuration#wiki-CommandExecution) and [fallback](https://github.com/Netflix/Hystrix/wiki/Configuration#wiki-CommandFallback)

## Hystrix collapser

Suppose you have some command which calls should be collapsed in one backend call. For this goal you can use HystrixCollapser annotation.

Hystrix command:
```java
    @HystrixCommand
    public User getUserById(String id) {
        return userResource.getUserById(id);
    }
```

Asynchronous call:
```java
    @HystrixCollapser(commandMethod = "getUserById",
            collapserProperties = {@HystrixProperty(name = "timerDelayInMilliseconds", value = "200")})
    public Future<User> getUserByIdCollapserAsync(String id) {
        return CollapserResult.async();
    }
```

Synchronous call:
```java
    @HystrixCollapser(commandMethod = "getUserById",
            collapserProperties = {@HystrixProperty(name = "maxRequestsInBatch", value = "3")})
    @Override
    public User getUserByIdCollapser(String id) {
        return CollapserResult.sync();
    }
```

Lines:
```java
    return CollapserResult.async();
    return CollapserResult.sync();
```
In examples above don't affect the result of a collapser call. This just to avoid ```return null;``` statement in code.
It's important to remember that Hystrix command and сollapser should be placed in the same class.

Read more about Hystrix request collapsing [here] (https://github.com/Netflix/Hystrix/wiki/How-it-Works#wiki-RequestCollapsing)

#Development Status and Future

```java
todo
```