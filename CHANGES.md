# Hystrix Releases #

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
