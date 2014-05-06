# Hystrix Releases #

### Version 1.4.0 Release Candidate 4 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.0-RC4%22)) ###

_NOTE: This code is NOT considered production worthy yet, hence the "Release Candidate" status._

This fixes some bugs and changes the `HystrixObservableCollapser` signature to support `Observable` batches.

* [Pull 256](https://github.com/Netflix/Hystrix/pull/256) Fix Race Condition on Timeout
* [Pull 261](https://github.com/Netflix/Hystrix/pull/261) New signature for HystrixObservableCollapser
* [Pull 254](https://github.com/Netflix/Hystrix/pull/254) Remove jsr305 Dependency
* [Pull 260](https://github.com/Netflix/Hystrix/pull/260) RxJava 0.18.2
* [Pull 253](https://github.com/Netflix/Hystrix/pull/253) Handling InterruptedExceptions in the HystrixMetricsStreamServlet

### Version 1.4.0 Release Candidate 3 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.0-RC3%22)) ###

_NOTE: This code is NOT considered production worthy yet, hence the "Release Candidate" status._

This adds non-blocking support to the collapser via the `HystrixObservableCollapser` type, fixes some bugs and upgrades to RxJava 0.18.

* [Pull 245](https://github.com/Netflix/Hystrix/pull/245) HystrixObservableCollapser
* [Pull 246](https://github.com/Netflix/Hystrix/pull/246) RxJava 0.18
* [Pull 250](https://github.com/Netflix/Hystrix/pull/250) Tripped CircuitBreaker Wouldn't Close Under Contention
* [Pull 243](https://github.com/Netflix/Hystrix/pull/243) Update servo to 0.6
* [Pull 240](https://github.com/Netflix/Hystrix/pull/240) Add missing reset for CommandExecutionHook in HystrixPlugins.UnitTest
* [Pull 244](https://github.com/Netflix/Hystrix/pull/244) Javanica: Cleaner error propagation 


### Version 1.4.0 Release Candidate 2 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.0-RC2%22)) ###

_NOTE: This code is NOT considered production worthy yet, hence the "Release Candidate" status._

This fixes a bug found in Release Candidate 1 that caused the semaphore limits to be applied when thread isolation was chosen.

It also stops scheduling callbacks onto new threads and lets the Hystrix threads perform the callbacks.

* [Pull 238](https://github.com/Netflix/Hystrix/pull/238) Fix for Semaphore vs Thread Isolation Bug
* [Pull 230](https://github.com/Netflix/Hystrix/pull/230) Javanica Module: Added support for Request Cache and Reactive Execution


### Version 1.4.0 Release Candidate 1 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.0-RC1%22)) ###

This is the first release candidate of 1.4.0 that includes `HystrixObservableCommand` ([Source](https://github.com/Netflix/Hystrix/blob/master/hystrix-core/src/main/java/com/netflix/hystrix/HystrixObservableCommand.java)) that supports bulkheading asynchronous, non-blocking sources.

_NOTE: This code is NOT considered production worthy yet, hence the "Release Candidate" status._

_It has run for 1 day on a single machine taking production traffic at Netflix. This is sufficient for us to proceed to a release candidate for official canary testing, but we intend to test for a week or two before doing a final release._

Here is a very basic example using Java 8 to make an HTTP call via Netty and receives a stream of chunks back:

```java

    public static void main(String args[]) {
        HystrixObservableCommand<String> command = bulkheadedNetworkRequest("www.google.com");
        command.toObservable()
                // using BlockingObservable.forEach for demo simplicity
                .toBlockingObservable().forEach(d -> System.out.println(d));
        System.out.println("Time: " + command.getExecutionTimeInMilliseconds() 
                + "  Events: " + command.getExecutionEvents());
    }

    public static HystrixObservableCommand<String> bulkheadedNetworkRequest(final String host) {
        return new HystrixObservableCommand<String>(HystrixCommandGroupKey.Factory.asKey("http")) {

            @Override
            protected Observable<String> run() {
                return directNetworkRequest(host);
            }

            @Override
            protected Observable<String> getFallback() {
                return Observable.just("Error 500");
            }

        };
    }

    public static Observable<String> directNetworkRequest(String host) {
        return RxNetty.createHttpClient(host, 80)
                .submit(HttpClientRequest.createGet("/"))
                .flatMap(response -> response.getContent())
                .map(data -> data.toString(Charset.defaultCharset()));
    }
```

* [Pull 218](https://github.com/Netflix/Hystrix/pull/218) Hystrix 1.4 - Async/Non-Blocking
* [Pull 217](https://github.com/Netflix/Hystrix/pull/217) Javanica: Added support for "Reactive Execution" and "Error Propagation"
* [Pull 219](https://github.com/Netflix/Hystrix/pull/219) Restore HystrixContext* Constructors without ConcurrencyStrategy

### Version 1.3.16 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.16%22)) ###

* [Pull 258](https://github.com/Netflix/Hystrix/pull/258) Skip CachedObservableOriginal when no getCacheKey()
* [Pull 259](https://github.com/Netflix/Hystrix/pull/259) Fix #257 with RxJava 0.18.2


### Version 1.3.15 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.15%22)) ###

* [Pull 248](https://github.com/Netflix/Hystrix/pull/248) RxJava 0.18 for Hystrix
* [Pull 249](https://github.com/Netflix/Hystrix/pull/249) Tripped CircuitBreaker Wouldn't Close Under Contention
* [Pull 229](https://github.com/Netflix/Hystrix/pull/229) Javanica: Added support for Request Cache and Reactive Execution
* [Pull 242](https://github.com/Netflix/Hystrix/pull/242) Javanica: Cleaner error propagation


### Version 1.3.14 (not released) ###

* [Pull 228](https://github.com/Netflix/Hystrix/pull/228) Upgrade 1.3.x to RxJava 0.17.1


### Version 1.3.13 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.13%22)) ###


* [Pull 216](https://github.com/Netflix/Hystrix/pull/216) hystrix-javanica contrib-module: [annotation support](https://github.com/Netflix/Hystrix/tree/master/hystrix-contrib/hystrix-javanica)


### Version 1.3.12 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.12%22)) ###

* [Pull 214](https://github.com/Netflix/Hystrix/pull/214) HystrixContextCallable/Runnable Constructors

### Version 1.3.11 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.11%22)) ###

* We'll ignore this release ever happened. Exact same binary as 1.3.10. (It helps to push code to Github before releasing.)

### Version 1.3.10 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.10%22)) ###

* [Pull 211](https://github.com/Netflix/Hystrix/pull/211) Update Javassist version to 3.18.1-GA
* [Pull 213](https://github.com/Netflix/Hystrix/pull/213) BugFix: Timeout does not propagate request context
* [Pull 204](https://github.com/Netflix/Hystrix/pull/204) deploying hystrix dashboard on tomcat, SLF4J complains about a missing implementation
* [Pull 199](https://github.com/Netflix/Hystrix/pull/199) Add new module for publishing metrics to Coda Hale Metrics version 3

### Version 1.3.9 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.9%22)) ###

* [Pull 210](https://github.com/Netflix/Hystrix/pull/210) HystrixContextScheduler was not wrapping the Inner Scheduler
* [Pull 206](https://github.com/Netflix/Hystrix/pull/206) Bugfix ExceptionThreadingUtility
* [Pull 203](https://github.com/Netflix/Hystrix/pull/203) Made HystrixTimer initialization thread-safe

### Version 1.3.8 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.8%22)) ###

* [Pull 194](https://github.com/Netflix/Hystrix/pull/194) BugFix: Do not overwrite user thread duration in case of timeouts

### Version 1.3.7 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.7%22)) ###

* [Pull 189](https://github.com/Netflix/Hystrix/pull/189) BugFix: Race condition between run() and timeout
* [Pull 190](https://github.com/Netflix/Hystrix/pull/190) BugFix: ConcurrencyStrategy.wrapCallable was not being used on callbacks


### Version 1.3.6 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.6%22)) ###

* [Pull 181](https://github.com/Netflix/Hystrix/pull/181) [hystrix-contrib/hystrix-clj] Making Command keys quantified by namespaces
* [Pull 182](https://github.com/Netflix/Hystrix/pull/182) Removing unused Legend component latent. Removed from html templates/css
* [Pull 183](https://github.com/Netflix/Hystrix/pull/183) Bugfix to HystrixBadRequestException handling
* [Pull 184](https://github.com/Netflix/Hystrix/pull/184) BugFix: queue() BadRequestException Handling on Cached Response
* [Pull 185](https://github.com/Netflix/Hystrix/pull/185) BugFix: Observable.observeOn Scheduler Lost RequestContext
* [0fb0d3d](https://github.com/Netflix/Hystrix/commit/0fb0d3d25e406f8b6240d312c2ee1f515c77fc13) RxJava 0.14

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
