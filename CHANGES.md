# Hystrix Releases #

### Version 1.3.5 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.5%22)) ###

* [Pull 179](https://github.com/Netflix/Hystrix/pull/179) RxJava 0.13

### Version 1.3.4 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.4%22)) ###

* [f68fa23c](https://github.com/Netflix/Hystrix/commit/bd6dfac5255753978253605f7e8b4c6af68fa23c) RxJava [0.11,0.12)

### Version 1.3.3 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.3%22)) ###

* [858e334f](https://github.com/Netflix/Hystrix/commit/7bcf0ee7b876cbfdcb942ea83637d4b5858e334f) RxJava 0.11

### Version 1.3.2 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.2%22)) ###

* [Pull 173](https://github.com/Netflix/Hystrix/pull/173) Fix Exception vs Throwable typo in preparation for RxJava 0.11.0

### Version 1.3.1 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.1%22)) ###

* [Pull 170](https://github.com/Netflix/Hystrix/pull/170) Add rx support to hystrix-clj

### Version 1.3.0 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.0%22)) ###

This version integrations Hystrix with [RxJava](https://github.com/Netflix/RxJava) to enable non-blocking reactive execution and functional composition.

Async execution can now be done reactively with the `observe()` method and it will callback when the value is received:

```java
Observable<String> s = new CommandHelloWorld("World").observe();
```

A simple example of subscribing to the value (using a Groovy lambda instead of anonymous inner class):

```groovy
s.subscribe({ value -> println(value) })
```

A "Hello World" example of reactive execution can be [found on the wiki](https://github.com/Netflix/Hystrix/wiki/How-To-Use#wiki-Reactive-Execution).

More can be learned about RxJava and the composition features at https://github.com/Netflix/RxJava/wiki

This release is a major refactoring of the Hystrix codebase. To assert correctness and performance it was run in production canary servers on the Netflix API several times during development and for over a week during release candidate stages. Prior to this release the 1.3.0.RC1 version has been running in full Netflix API production for several days performing billions of executions a day.

* [Pull 151](https://github.com/Netflix/Hystrix/pull/151) Version 1.3 - RxJava Observable Integration
* [Pull 158](https://github.com/Netflix/Hystrix/pull/158) Expose current HystrixCommand to fns


### Version 1.2.18 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.18%22)) ###

* [Pull 152](https://github.com/Netflix/Hystrix/pull/152) Escape meta-characters to fix dashboard
* [Pull 156](https://github.com/Netflix/Hystrix/pull/156) Improve hystrix-clj docs
* [Pull 155](https://github.com/Netflix/Hystrix/pull/155) Reset Hystrix after hystrix-clj tests have run

### Version 1.2.17 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.17%22)) ###

* [Pull 138](https://github.com/Netflix/Hystrix/pull/138) Eclipse and IDEA Config
* [Pull 141](https://github.com/Netflix/Hystrix/pull/141) Upgrade Clojuresque (hystrix-clj builds)
* [Pull 139](https://github.com/Netflix/Hystrix/pull/139) Fix dashboard math bug on thread pool rate calculations
* [Pull 149](https://github.com/Netflix/Hystrix/pull/149) Allow getFallback to query failure states

### Version 1.2.16 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.16%22)) ###

* [Pull 132](https://github.com/Netflix/Hystrix/pull/132) Add `with-context` macro for conviently wrapping collapsers in thier own context
* [Pull 136](https://github.com/Netflix/Hystrix/pull/136) Fixed the mock stream
* [Pull 137](https://github.com/Netflix/Hystrix/pull/137) Limit scope of CurrentThreadExecutingCommand

### Version 1.2.15 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.15%22)) ###

This is fixing a bug introduced in the last release that affects semaphore isolated commands that use request caching.

* [Pull 133](https://github.com/Netflix/Hystrix/pull/133) Fix NoSuchElement Exception

### Version 1.2.14 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.14%22)) ###

* [Issue 116](https://github.com/Netflix/Hystrix/issues/116) Mechanism for Auditing Network Access Not Isolated by Hystrix
 
A new module for instrumenting network access to identify calls not wrapped by Hystrix.

See the module README for more information: https://github.com/Netflix/Hystrix/tree/master/hystrix-contrib/hystrix-network-auditor-agent

### Version 1.2.13 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.13%22)) ###

* [Issue 127](https://github.com/Netflix/Hystrix/issues/127) Add destroy() method to MetricsServlet

### Version 1.2.12 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.12%22)) ###

* [Issue 124](https://github.com/Netflix/Hystrix/issues/124) NPE if Hystrix.reset() called when it's already shutdown

### Version 1.2.11 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.11%22)) ###

* [Issue 113](https://github.com/Netflix/Hystrix/issues/113) IllegalStateException: Future Not Started (on thread pool rejection with response caching)
* [Issue 118](https://github.com/Netflix/Hystrix/issues/118) Semaphore counter scope was global instead of per-key
* [Pull 121](https://github.com/Netflix/Hystrix/issues/121) Concurrent execution metric for semaphore and thread isolation

### Version 1.2.10 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.10%22)) ###

* [Issue 80](https://github.com/Netflix/Hystrix/issues/80) HystrixCollapser Concurrency and Performance Fixes

### Version 1.2.9 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.9%22)) ###

* [Issue 109](https://github.com/Netflix/Hystrix/issues/109) Hystrix.reset() now shuts down HystrixTimer
* [Pull 110](https://github.com/Netflix/Hystrix/issues/110) hystrix-clj cleanup
* [Pull 112](https://github.com/Netflix/Hystrix/issues/112) Further work on HystrixCollapser IllegalStateException ([Issue 80](https://github.com/Netflix/Hystrix/issues/80))

### Version 1.2.8 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.8%22)) ###

* [Issue 102](https://github.com/Netflix/Hystrix/issues/102) Hystrix.reset() functionality for clean shutdown and resource cleanup
* [Pull 103](https://github.com/Netflix/Hystrix/issues/103) hystrix-clj cleanup
* [Pull 104](https://github.com/Netflix/Hystrix/issues/104) javadoc clarification
* [Pull 105](https://github.com/Netflix/Hystrix/issues/104) Added IntelliJ IDEA support, cleanup to Eclipse support

### Version 1.2.7 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.7%22)) ###

* [Pull 99](https://github.com/Netflix/Hystrix/issues/99) Experimental Clojure Bindings [hystrix-clj](https://github.com/Netflix/Hystrix/tree/master/hystrix-contrib/hystrix-clj)

### Version 1.2.6 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.6%22)) ###

* [Issue 96](https://github.com/Netflix/Hystrix/issues/96) Remove 'final' modifiers to allow mocking

### Version 1.2.5 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.5%22)) ###

* [Pull 94](https://github.com/Netflix/Hystrix/pull/94) Force character encoding for event stream to utf-8
* [Issue 60](https://github.com/Netflix/Hystrix/issues/60) Dashboard: Hover for full name (when shortened with ellipsis)
* [Issue 53](https://github.com/Netflix/Hystrix/issues/53) RequestLog: Reduce Chance of Memory Leak
 
### Version 1.2.4 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.4%22)) ###

* [Pull 91](https://github.com/Netflix/Hystrix/pull/91) handle null circuit breaker in HystrixMetricsPoller

### Version 1.2.3 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.3%22)) ###

* [Issue 85](https://github.com/Netflix/Hystrix/issues/85) hystrix.stream holds connection open if no metrics
* [Pull 84](https://github.com/Netflix/Hystrix/pull/84) include 'provided' dependencies in Eclipse project classpath

### Version 1.2.2 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.2%22)) ###

* [Issue 82](https://github.com/Netflix/Hystrix/issues/82) ThreadPool stream should include reportingHosts

### Version 1.2.1 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.1%22)) ###

* [Issue 80](https://github.com/Netflix/Hystrix/issues/80) IllegalStateException: Future Not Started
* [Issue 78](https://github.com/Netflix/Hystrix/issues/78) Include more info when collapsed requests remain in queue

### Version 1.2.0 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.2.0%22)) ###

* [Issue 10](https://github.com/Netflix/Hystrix/issues/10) HystrixCommand Execution Hooks via Plugin
  * [Pull 71](https://github.com/Netflix/Hystrix/pull/71) Change Throwable to Exception 
  * [Pull 71](https://github.com/Netflix/Hystrix/pull/71) jettyRun support for running webapps via gradle
* [Issue 15](https://github.com/Netflix/Hystrix/issues/15) Property to disable percentile calculations
* [Issue 69](https://github.com/Netflix/Hystrix/issues/69) Property to disable fallbacks
* [Pull 73](https://github.com/Netflix/Hystrix/pull/73) Make servlet-api a provided dependency
* [Pull 74](https://github.com/Netflix/Hystrix/pull/74) Dashboard problem when using Turbine (Stream not flushing)

### Version 1.1.7 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.1.7%22)) ###

* [Pull 67](https://github.com/Netflix/Hystrix/pull/67) Unit tests for request log and checked exceptions
* [Pull 66](https://github.com/Netflix/Hystrix/pull/66) Making provided scope transtive
* [Pull 65](https://github.com/Netflix/Hystrix/pull/65) Fixed gitignore definition of build output directories
* [Issue 63](https://github.com/Netflix/Hystrix/issues/63) Add "throws Exception" to HystrixCommand run() method
* [Pull 62](https://github.com/Netflix/Hystrix/pull/62) applying js fixes to threadPool ui
* [Pull 61](https://github.com/Netflix/Hystrix/pull/61) Request log with timeouts
* [Issue 55](https://github.com/Netflix/Hystrix/issues/55) HysrixRequestLog: Missing Events and Time on Timeouts
* [Issue 20](https://github.com/Netflix/Hystrix/issues/20) TotalExecutionTime not tracked on queue()
* [Pull 57](https://github.com/Netflix/Hystrix/pull/57) Dashboard js fix
* [Issue 39](https://github.com/Netflix/Hystrix/issues/39) HystrixPlugins Bootstrapping Problem - Race Conditions
* [Pull 52](https://github.com/Netflix/Hystrix/pull/52) Gradle Build Changes

### Version 1.1.6 ###

* [Pull 51](https://github.com/Netflix/Hystrix/pull/51) Merging in gradle-template, specifically provided

### Version 1.1.5 ###

* [Pull 50](https://github.com/Netflix/Hystrix/pull/50) Make javax.servlet-api a 'provided' dependency not 'compile'

### Version 1.1.4 ###

* [Pull 49](https://github.com/Netflix/Hystrix/pull/49) Cleaner design (for metrics) by injecting listener into constructor.

### Version 1.1.3 ###

* [Pull 47](https://github.com/Netflix/Hystrix/pull/47) Support pausing/resuming metrics poller
* [Pull 48](https://github.com/Netflix/Hystrix/pull/48) Fixing non-deterministic unit test
* README files added to submodules

### Version 1.1.2 ###

* [Pull 44](https://github.com/Netflix/Hystrix/pull/44) Hystrix Dashboard

### Version 1.1.1 ###

* [Issue 24](https://github.com/Netflix/Hystrix/issues/24) Yammer Metrics Support
* [Pull 43](https://github.com/Netflix/Hystrix/pull/43) Fix the wrong percentile for latencyExecute_percentile_75 in the Servo publisher

### Version 1.1.0 ###

* [Pull 32](https://github.com/Netflix/Hystrix/pull/32) servo-event-stream module
* [Pull 33](https://github.com/Netflix/Hystrix/pull/33) Remove Servo dependency from core, move to submodule
* [Pull 35](https://github.com/Netflix/Hystrix/pull/35) Metrics event stream
* [Issue 34](https://github.com/Netflix/Hystrix/issues/34) Remove Strategy Injection on HystrixCommand
* [Pull 36](https://github.com/Netflix/Hystrix/pull/36) example webapp
* [Pull 37](https://github.com/Netflix/Hystrix/pull/37) Migrate metrics stream from org.json.JSONObject to Jackson

### Version 1.0.3 ###

* [Pull 4](https://github.com/Netflix/Hystrix/pull/4) Contrib request context servlet filters 
* [Pull 16](https://github.com/Netflix/Hystrix/pull/16) Change logger from info to debug for property changes
* [Issue 12](https://github.com/Netflix/Hystrix/issues/12) Use logger.error not logger.debug for fallback failure
* [Issue 8](https://github.com/Netflix/Hystrix/issues/8) Capture exception from run() and expose getter
* [Issue 22](https://github.com/Netflix/Hystrix/issues/22) Default Collapser scope to REQUEST if using Setter
* [Pull 27](https://github.com/Netflix/Hystrix/pull/27) Initialize HealthCounts to non-null value
* [Issue 28](https://github.com/Netflix/Hystrix/issues/28) Thread pools lost custom names in opensource refactoring
* [Pull 30](https://github.com/Netflix/Hystrix/pull/30) Simplified access to HystrixCommandMetrics
* Javadoc and README changes

### Version 1.0.2 ###

* Javadoc changes

### Version 1.0.0 ###

* Initial open source release 
