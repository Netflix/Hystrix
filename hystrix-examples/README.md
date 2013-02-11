## hystrix-examples

This module contains examples of using [HystrixCommand](https://github.com/Netflix/Hystrix/tree/master/hystrix-core/src/main/java/com/netflix/hystrix/HystrixCommand.java), [HystrixCollapser](https://github.com/Netflix/Hystrix/tree/master/hystrix-core/src/main/java/com/netflix/hystrix/HystrixCollapser.java) and other aspects of Hystrix.

## Documentation

Documentation related to the examples in this module is on the [How To Use](https://github.com/Netflix/Hystrix/wiki/How-To-Use) page.

## Demo

To run a [demo app](https://github.com/Netflix/Hystrix/tree/master/hystrix-examples/src/main/java/com/netflix/hystrix/examples/demo/HystrixCommandDemo.java) do the following:

```
$ git clone git@github.com:Netflix/Hystrix.git
$ cd Hystrix/
./gradlew runDemo
```

You will see output similar to the following:

```
Request => GetUserAccountCommand[SUCCESS][8ms], GetPaymentInformationCommand[SUCCESS][20ms], GetUserAccountCommand[SUCCESS, RESPONSE_FROM_CACHE][0ms]x2, GetOrderCommand[SUCCESS][101ms], CreditCardCommand[SUCCESS][1075ms]
Request => GetUserAccountCommand[FAILURE, FALLBACK_SUCCESS][2ms], GetPaymentInformationCommand[SUCCESS][22ms], GetUserAccountCommand[FAILURE, FALLBACK_SUCCESS, RESPONSE_FROM_CACHE][0ms]x2, GetOrderCommand[SUCCESS][130ms], CreditCardCommand[SUCCESS][1050ms]
Request => GetUserAccountCommand[FAILURE, FALLBACK_SUCCESS][4ms], GetPaymentInformationCommand[SUCCESS][19ms], GetUserAccountCommand[FAILURE, FALLBACK_SUCCESS, RESPONSE_FROM_CACHE][0ms]x2, GetOrderCommand[SUCCESS][145ms], CreditCardCommand[SUCCESS][1301ms]
Request => GetUserAccountCommand[SUCCESS][4ms], GetPaymentInformationCommand[SUCCESS][11ms], GetUserAccountCommand[SUCCESS, RESPONSE_FROM_CACHE][0ms]x2, GetOrderCommand[SUCCESS][93ms], CreditCardCommand[SUCCESS][1409ms]

#####################################################################################
# CreditCardCommand: Requests: 17 Errors: 0 (0%)   Mean: 1171 75th: 1391 90th: 1470 99th: 1486 
# GetOrderCommand: Requests: 21 Errors: 0 (0%)   Mean: 100 75th: 144 90th: 207 99th: 230 
# GetUserAccountCommand: Requests: 21 Errors: 4 (19%)   Mean: 8 75th: 11 90th: 46 99th: 51 
# GetPaymentInformationCommand: Requests: 21 Errors: 0 (0%)   Mean: 18 75th: 21 90th: 24 99th: 25 
#####################################################################################

Request => GetUserAccountCommand[SUCCESS][10ms], GetPaymentInformationCommand[SUCCESS][16ms], GetUserAccountCommand[SUCCESS, RESPONSE_FROM_CACHE][0ms]x2, GetOrderCommand[SUCCESS][51ms], CreditCardCommand[SUCCESS][922ms]
Request => GetUserAccountCommand[SUCCESS][12ms], GetPaymentInformationCommand[SUCCESS][12ms], GetUserAccountCommand[SUCCESS, RESPONSE_FROM_CACHE][0ms]x2, GetOrderCommand[SUCCESS][68ms], CreditCardCommand[SUCCESS][1257ms]
Request => GetUserAccountCommand[SUCCESS][10ms], GetPaymentInformationCommand[SUCCESS][11ms], GetUserAccountCommand[SUCCESS, RESPONSE_FROM_CACHE][0ms]x2, GetOrderCommand[SUCCESS][78ms], CreditCardCommand[SUCCESS][1295ms]
Request => GetUserAccountCommand[FAILURE, FALLBACK_SUCCESS][6ms], GetPaymentInformationCommand[SUCCESS][11ms], GetUserAccountCommand[FAILURE, FALLBACK_SUCCESS, RESPONSE_FROM_CACHE][0ms]x2, GetOrderCommand[SUCCESS][153ms], CreditCardCommand[SUCCESS][1321ms]
```

This demo simulates 4 different [HystrixCommand](https://github.com/Netflix/Hystrix/tree/master/hystrix-core/src/main/java/com/netflix/hystrix/HystrixCommand.java) implementations with failures, latency, timeouts and duplicate calls in a multi-threaded environment.

It logs the results of [HystrixRequestLog](https://github.com/Netflix/Hystrix/tree/master/hystrix-core/src/main/java/com/netflix/hystrix/HystrixRequestLog.java) and metrics from [HystrixCommandMetrics](https://github.com/Netflix/Hystrix/tree/master/hystrix-core/src/main/java/com/netflix/hystrix/HystrixCommandMetrics.java).



## Maven Central

Binaries and dependencies for this module can be found on [http://search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20a%3A%22hystrix-examples%22).

__GroupId: com.netflix.hystrix__  
__ArtifactId: hystrix-examples__  

Example for Maven:

```xml
<dependency>
    <groupId>com.netflix.hystrix</groupId>
    <artifactId>hystrix-examples</artifactId>
    <version>1.0.2</version>
</dependency>
```
and for Ivy:

```xml
<dependency org="com.netflix.hystrix" name="hystrix-examples" rev="1.0.2" />
```
