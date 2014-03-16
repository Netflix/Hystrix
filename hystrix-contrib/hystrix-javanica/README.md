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
### Synchronous Execution

To run method as Hystrix command synchronously you need to annotate method with `@HystrixCommand` annotation, for example
```java
public class UserService {
...
    @HystrixCommand
    public User getUserById(String id) {
        return userResource.getUserById(id);
    }
}
...
```
In example above the `getUserById` method will be processed [synchronously](https://github.com/Netflix/Hystrix/wiki/How-To-Use#wiki-Synchronous-Execution) within new Hystrix command. 
By default the name of **command key** is command method name: `getUserById`, default **group key** name is class name of annotated method: `UserService`. You can change it using necessary `@HystrixCommand` properties:

```java
    @HystrixCommand(groupKey="UserGroup", commandKey = "GetUserByIdCommand")
    public User getUserById(String id) {
        return userResource.getUserById(id);
    }
```
To set threadPoolKey use ```@HystrixCommand#threadPoolKey()```

### Asynchronous Execution

To process Hystrix command asynchronously you should return an instance of `AsyncCommand` in your command method as in the exapmple below:
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

## Reactive Execution

To performe "Reactive Execution" you should return an instance of `ObservableCommand` in your command method as in the exapmple below:

```java
    @HystrixCommand
    public Observable<User> getUserById(final String id) {
        return new ObservableCommand<User>() {
            @Override
            public User invoke() {
                return userResource.getUserById(id);
            }
        };
    }
```

The return type of command method should be `Observable`.

## Fallback

Graceful degradation can be achieved by declaring name of fallback method in `@HystrixCommand` like below:

```java
    @HystrixCommand(fallbackMethod = "defaultUser")
    public User getUserById(String id) {
        return userResource.getUserById(id);
    }

    private User defaultUser(String id) {
        return new User("def", "def");
    }
```

**_Its important to remember that Hystrix command and fallback should be placed in the same class and have same method signature_**.

Fallback method can have any access modifier. Method `defaultUser` will be used to process fallback logic in a case of any errors. If you need to run fallback method `defaultUser` as separate Hystrix command then you need to annotate it with `HystrixCommand` annotation as below:
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

If fallback method was marked with `@HystrixCommand` then this fallback method (_defaultUser_) also can has own fallback method, as in the example below:
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

### Error Propagation
Based on [this](https://github.com/Netflix/Hystrix/wiki/How-To-Use#ErrorPropagation) description, `@HystrixCommand` has an ability to specify exceptions types which should be ignored and wrapped to throw in `HystrixBadRequestException`.

```java
    @HystrixCommand(ignoreExceptions = {BadRequestException.class})
    public User getUserById(String id) {
        return userResource.getUserById(id);
    }
```

If `userResource.getUserById(id);` throws an exception which type is _BadRequestException_ then this exception will be wrapped in `HystrixBadRequestException` and will not affect metrics and will not trigger fallback logic.

## Request Cache

Request caching is enabled by defining the _get cache key_ method like in example below:
```java
    @HystrixCommand(cacheKeyMethod = "getUserIdCacheKey")
    public User getUserById(String id) {
        return userResource.getUserById(id);
    }

    private String getUserIdCacheKey(String id){
        return id;
    }
```

If "getUserIdCacheKey" returns `null` then the cache key will be "null" (string).

**_Its important to remember that Hystrix command and "get cache key" method should be placed in the same class and have same method signature_**.

## Configuration
### Command Properties

Command properties can be set using @HystrixCommand's 'commandProperties' like below:

```java
    @HystrixCommand(commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "500")
        })
    public User getUserById(String id) {
        return userResource.getUserById(id);
    }
```

Javanica dynamically sets properties using Hystrix ConfigurationManager.
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
In examples above don't affect the result of a collapser call. This just to avoid `return null;` statement in code.
It's important to remember that Hystrix command and сollapser should be placed in the same class.

To set collapser [properties](https://github.com/Netflix/Hystrix/wiki/Configuration#Collapser) use `@HystrixCollapser#collapserProperties`

Read more about Hystrix request collapsing [here] (https://github.com/Netflix/Hystrix/wiki/How-it-Works#wiki-RequestCollapsing)

#Development Status and Future
Todo list:

1. Add new annotation that can be applied for types in order to define default command properties for all commands that are specified in a class. For example specify default group key or default properties for all commands.
2. Add ability to make a command to provide collapser functionality as an alternative for `@HystrixCollapser`, 
concept/sample `@HystrixCommand#collapser() : boolean`
3. Change code to use new `HystrixObservableCommand`
4.Add Cache annotation to mark some parameters which should participate in building cache key, for example:

```java
@HystrixCommand
User getUserByParams(@Cache String id, String name, @Cache Integer age)
```
`toString()` method will be invoked for parameters: id and age to build cache key:
Pseudo code
```
String cacheKey = id.toString()+age.toString()
```
