# Hystrix: Latency and Fault Tolerance for Distributed Systems

Hystrix is a latency and fault tolerance library designed to isolate points of access to remote systems, services and 3rd party libraries, stop cascading failure and enable resilience in complex distributed systems where failure is inevitable.

## Full Documentation

See the [Wiki](Hystrix/wiki) for full documentation, examples, operational details and other information.

See the [Javadoc](http://netflix.github.com/Hystrix/javadoc) for the API.

## What does it do?

#### 1) Latency and Fault Tolerance

Stop cascading failures. Fallbacks and graceful degradation. Fail fast and rapid recovery. 

Thread and semaphore isolation with circuit breakers. 

#### 2) Realtime Operations

Realtime monitoring and configuration changes. Watch service and property changes take effect immediately as they spread across a fleet. 

Be alerted, make decisions, affect change and see results in seconds. 

#### 3) Concurrency

Parallel execution. Concurrency aware request caching. Automated batching through request collapsing.

## Download or Build

You need Java 6 or later.

Binaries and dependency information for Maven, Ivy, Gradle and others can be found at [http://search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20a%3A%22hystrix-core%22).

Details on building can be found on the [Getting Started](Hystrix/wiki/Getting-Started) page of the wiki.

To build:

```java
$ git clone git@github.com:Netflix/Hystrix.git
$ cd Hystrix/
$ ./gradlew build
```


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
```

More examples and information can be found in the [How To Use](Hystrix/wiki/How-To-Use) section.

Example source code can be found in the [hystrix-examples](Hystrix/tree/master/hystrix-examples/src/main/java/com/netflix/hystrix/examples) module.


## Bugs and Feedback

For bugs, questions and discussions please use the [Github Issues](Hystrix/issues).

 
## LICENSE

Copyright 2012 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
