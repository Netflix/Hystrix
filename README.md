<img src="https://netflix.github.com/Hystrix/images/hystrix-logo-tagline-850.png">

# Hystrix: Latency and Fault Tolerance for Distributed Systems

[![NetflixOSS Lifecycle](https://img.shields.io/osslifecycle/Netflix/PROJECTNAMEHERE.svg)]()
[![][travis img]][travis]
[![][maven img]][maven]
[![][license img]][license]

Hystrix is a latency and fault tolerance library designed to isolate points of access to remote systems, services and 3rd party libraries, stop cascading failure and enable resilience in complex distributed systems where failure is inevitable.

## Full Documentation

See the [Wiki](https://github.com/Netflix/Hystrix/wiki/) for full documentation, examples, operational details and other information.

See the [Javadoc](http://netflix.github.com/Hystrix/javadoc) for the API.

## Communication

- Google Group: [HystrixOSS](http://groups.google.com/d/forum/hystrixoss)
- Twitter: [@HystrixOSS](http://twitter.com/HystrixOSS)
- [GitHub Issues](https://github.com/Netflix/Hystrix/issues)

## What does it do?

#### 1) Latency and Fault Tolerance

Stop cascading failures. Fallbacks and graceful degradation. Fail fast and rapid recovery. 

Thread and semaphore isolation with circuit breakers. 

#### 2) Realtime Operations

Realtime monitoring and configuration changes. Watch service and property changes take effect immediately as they spread across a fleet. 

Be alerted, make decisions, affect change and see results in seconds. 

#### 3) Concurrency

Parallel execution. Concurrency aware request caching. Automated batching through request collapsing.

## Hello World!

Code to be isolated is wrapped inside the run() method of a HystrixCommand similar to the following:

```java
public class CommandHelloWorld extends HystrixCommand<String> {

    private final String name;

    public CommandHelloWorld(String name) {
        super(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
        this.name = name;
    }

    @Override
    protected String run() {
        return "Hello " + name + "!";
    }
}
```

This command could be used like this:

```java
String s = new CommandHelloWorld("Bob").execute();
Future<String> s = new CommandHelloWorld("Bob").queue();
Observable<String> s = new CommandHelloWorld("Bob").observe();
```

More examples and information can be found in the [How To Use](https://github.com/Netflix/Hystrix/wiki/How-To-Use) section.

Example source code can be found in the [hystrix-examples](https://github.com/Netflix/Hystrix/tree/master/hystrix-examples/src/main/java/com/netflix/hystrix/examples) module.

## Binaries

Binaries and dependency information for Maven, Ivy, Gradle and others can be found at [http://search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20a%3A%22hystrix-core%22).

Change history and version numbers => [CHANGELOG.md](https://github.com/Netflix/Hystrix/blob/master/CHANGELOG.md)

Example for Maven:

```xml
<dependency>
    <groupId>com.netflix.hystrix</groupId>
    <artifactId>hystrix-core</artifactId>
    <version>x.y.z</version>
</dependency>
```
and for Ivy:

```xml
<dependency org="com.netflix.hystrix" name="hystrix-core" rev="x.y.z" />
```

If you need to download the jars instead of using a build system, create a Maven pom file like this with the desired version:

```xml
<?xml version="1.0"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<groupId>com.netflix.hystrix.download</groupId>
	<artifactId>hystrix-download</artifactId>
	<version>1.0-SNAPSHOT</version>
	<name>Simple POM to download hystrix-core and dependencies</name>
	<url>http://github.com/Netflix/Hystrix</url>
	<dependencies>
		<dependency>
			<groupId>com.netflix.hystrix</groupId>
			<artifactId>hystrix-core</artifactId>
			<version>x.y.z</version>
			<scope/>
		</dependency>
	</dependencies>
</project>
```

Then execute:

```
mvn -f download-hystrix-pom.xml dependency:copy-dependencies
```

It will download hystrix-core-*.jar and its dependencies into ./target/dependency/.

You need Java 6 or later.

## Build

To build:

```
$ git clone git@github.com:Netflix/Hystrix.git
$ cd Hystrix/
$ ./gradlew build
```

Futher details on building can be found on the [Getting Started](https://github.com/Netflix/Hystrix/wiki/Getting-Started) page of the wiki.

## Run Demo

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

## Dashboard

A dashboard for monitoring applications using Hystrix is available in the [hystrix-dashboard](https://github.com/Netflix/Hystrix/tree/master/hystrix-dashboard) module.

More information can be found on the [Dashboard Wiki](https://github.com/Netflix/Hystrix/wiki/Dashboard).

<img src="https://raw.github.com/wiki/Netflix/Hystrix/images/hystrix-dashboard-single-row.png">

## Bugs and Feedback

For bugs, questions and discussions please use the [GitHub Issues](https://github.com/Netflix/Hystrix/issues).

 
## LICENSE

Copyright 2013 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


[travis]:https://travis-ci.org/Netflix/Hystrix
[travis img]:https://travis-ci.org/Netflix/Hystrix.svg?branch=master

[maven]:http://search.maven.org/#search|gav|1|g:"com.netflix.hystrix"%20AND%20a:"hystrix-core"
[maven img]:https://maven-badges.herokuapp.com/maven-central/com.netflix.hystrix/hystrix-core/badge.svg

[release]:https://github.com/netflix/hystrix/releases
[release img]:https://img.shields.io/github/release/netflix/hystrix.svg

[license]:LICENSE-2.0.txt
[license img]:https://img.shields.io/badge/License-Apache%202-blue.svg
