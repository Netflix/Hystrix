# Hystrix Releases #

### Version 1.4.0 Release Candidate 9 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.0-rc.9%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.0-rc.9/)) ###
_NOTE: This code is believed to be production worthy.  As of now, there are no known bugs preventing this becoming 1.4.0.  Please report any design issues/questions, bugs, or any observations about this release to the [Issues](https://github.com/Netflix/Hystrix/issues) page._

* [Pull 697](https://github.com/Netflix/Hystrix/pull/697) Add execution event for FALLBACK_REJECTION and unit tests
* [Pull 696](https://github.com/Netflix/Hystrix/pull/696) Upgrade to RxJava 1.0.7
* [Pull 694](https://github.com/Netflix/Hystrix/pull/694) Make execution timeout in HystrixCommandProperties work in the case when classes extend HystrixCommandProperties
* [Pull 693](https://github.com/Netflix/Hystrix/pull/693) Hystrix Dashboard sorting issue

### Version 1.4.0 Release Candidate 8 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.0-rc.8%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.0-rc.8/)) ###
_NOTE: This code is believed to be production worthy.  As of now, there are no known bugs preventing this becoming 1.4.0.  Please report any design issues/questions, bugs, or any observations about this release to the [Issues](https://github.com/Netflix/Hystrix/issues) page._

* [Pull 691](https://github.com/Netflix/Hystrix/pull/691) HystrixCommandTest test with large number of semaphores
* [Pull 690](https://github.com/Netflix/Hystrix/pull/690) Add back ExceptionThreadingUtility
* [Pull 688](https://github.com/Netflix/Hystrix/pull/688) Add metrics for EMIT and FALLBACK_EMIT
* [Pull 687](https://github.com/Netflix/Hystrix/pull/687) Fixed issue where fallback rejection was also incrementing fallback failure metric
* [Pull 686](https://github.com/Netflix/Hystrix/pull/686) HystrixCommandTest Unit test refactoring
* [Pull 683](https://github.com/Netflix/Hystrix/pull/683) Add and Deprecate pieces of execution hook API to be more consistent
* [Pull 681](https://github.com/Netflix/Hystrix/pull/681) Add cache hit execution hook
* [Pull 680](https://github.com/Netflix/Hystrix/pull/680) Add command rejection metrics for HystrixThreadPools


### Version 1.4.0 Release Candidate 7 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.0-rc.7%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.0-rc.7/)) ###
NOTE: This code is believed to be production worthy.  As of now, there are no known bugs preventing this becoming 1.4.0.  Please report any design issues/questions, bugs, or any observations about this release to the [Issues](https://github.com/Netflix/Hystrix/issues) page

* [Pull 678](https://github.com/Netflix/Hystrix/pull/678) Fix current concurrent execution count
* [Pull 676](https://github.com/Netflix/Hystrix/pull/676) Add test to confirm that bad requests do not affect circuit breaker's computed error percentage
* [Pull 675](https://github.com/Netflix/Hystrix/pull/675) Deprecate method names for executionTimeout that are thread-specific
* [Pull 672](https://github.com/Netflix/Hystrix/pull/672) Limit the thread-interrupt behavior to occur only on timeouts
* [Pull 669](https://github.com/Netflix/Hystrix/pull/669) Added unit tests to demonstrate non-blocking semaphore timeout
* [Pull 667](https://github.com/Netflix/Hystrix/pull/667) Added rolling max counter for command execution
* [Pull 666](https://github.com/Netflix/Hystrix/pull/666) Added missing licenses
* [Pull 665](https://github.com/Netflix/Hystrix/pull/665) Added comment to HystrixConcurrencyStrategy about non-idempotency of strategy application
* [Pull 647](https://github.com/Netflix/Hystrix/pull/647) Tie command property to thread interrupt
* [Pull 645](https://github.com/Netflix/Hystrix/pull/645) Remove incorrect reference to async timeout
* [Pull 644](https://github.com/Netflix/Hystrix/pull/644) Add RequestCollapser metrics to Yammer Metrics Publisher
* [Pull 643](https://github.com/Netflix/Hystrix/pull/643) Stress-test HystrixObservalbeCollapser
* [Pull 642](https://github.com/Netflix/Hystrix/pull/642) Fix flakiness of HystrixObservableCommandTest.testRejectedViaSemaphoreIsolation
* [Pull 641](https://github.com/Netflix/Hystrix/pull/641) Fix flakiness of testSemaphorePermitsInUse
* [Pull 608](https://github.com/Netflix/Hystrix/pull/608) Make HystrixObservableCommand handle both sync and async exceptions
* [Pull 607](https://github.com/Netflix/Hystrix/pull/607) Upgrade RxJava from 1.0.4 to 1.0.5
* [Pull 604](https://github.com/Netflix/Hystrix/pull/604) Added EMIT and FALLBACK_EMIT event types that get emitted in HystrixObservableCommand
* [Pull 599](https://github.com/Netflix/Hystrix/pull/599) Added metrics to HystrixObservableCollapser
* [Pull 596](https://github.com/Netflix/Hystrix/pull/596) Fixed HystrixContextScheduler to conform with RxJava Worker contract
* [Pull 583](https://github.com/Netflix/Hystrix/pull/583) Style and consistency fixes
* [Pull 582](https://github.com/Netflix/Hystrix/pull/582) Add more unit tests for non-blocking HystrixCommand.queue()
* [Pull 580](https://github.com/Netflix/Hystrix/pull/580) Unit test to demonstrate fixed non-blocking timeout for HystrixCommand.queue()
* [Pull 579](https://github.com/Netflix/Hystrix/pull/579) Remove synchronous timeout
* [Pull 577](https://github.com/Netflix/Hystrix/pull/577) Upgrade language level to Java7
* [Pull 576](https://github.com/Netflix/Hystrix/pull/576) Add request collapser metrics
* [Pull 573](https://github.com/Netflix/Hystrix/pull/573) Fix link to CHANGELOG.md in README.md
* [Pull 572](https://github.com/Netflix/Hystrix/pull/572) Add bad request metrics at the command granularity
* [Pull 567](https://github.com/Netflix/Hystrix/pull/567) Comment out flaky unit-tests
* [Pull 566](https://github.com/Netflix/Hystrix/pull/566) Fix hardcoded groupname in CodaHale metrics publisher
* [Commit 6c08d9](https://github.com/Netflix/Hystrix/commit/6c08d90fd10a947bdbee81afc9c0f866d1f33eef) Bumped nebula.netflixoss from 2.2.3 to 2.2.5
* [Pull 562](https://github.com/Netflix/Hystrix/pull/562) Build changes to nebula.netflixoss (initially submitted as [Pull 469](https://github.com/Netflix/Hystrix/pull/469))
	
	
### Version 1.4.0 Release Candidate 6 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.0-RC6%22)) ###

_NOTE: This code is believed to be production worthy but is still a "Release Candidate" until [these possible functional or design issues are resolved](https://github.com/Netflix/Hystrix/issues?q=is%3Aopen+is%3Aissue+milestone%3A1.4.0-RC7)._
* [Pull 534](https://github.com/Netflix/Hystrix/pull/534) Bump RxJava to 1.0.4
* [Pull 532](https://github.com/Netflix/Hystrix/pull/532) Hystrix-Clojure: Fix typo
* [Pull 531](https://github.com/Netflix/Hystrix/pull/531) Move onThreadStart execution hook after check that wrapping thread timed out 
* [Pull 530](https://github.com/Netflix/Hystrix/pull/530) Add shutdown hook to metrics servlet for WebSphere
* [Pull 527](https://github.com/Netflix/Hystrix/pull/527) Creating a synthetic exception in the semaphore execution and short-circuited case
* [Pull 526](https://github.com/Netflix/Hystrix/pull/526) Move onRunSuccess/onRunError and thread-pool book-keeping to Hystrix thread
* [Pull 524](https://github.com/Netflix/Hystrix/pull/524) Change calls from getExecutedCommands() to getAllExecutedCommands()
* [Pull 516](https://github.com/Netflix/Hystrix/pull/516) Updated HystrixServoMetricsPublisher initalization of singleton
* [Pull 489](https://github.com/Netflix/Hystrix/pull/489) Javanica: Request Caching
* [Pull 512](https://github.com/Netflix/Hystrix/pull/512) Add execution hook Javadoc
* [Pull 511](https://github.com/Netflix/Hystrix/pull/511) Fix missing onComplete hook call when command short-circuits and missing onRunSuccess hook call in thread-timeout case
* [Pull 466](https://github.com/Netflix/Hystrix/pull/466) Add unit tests to HystrixCommand and HystrixObservableCommand
* [Pull 465](https://github.com/Netflix/Hystrix/pull/465) Handle error in construction of HystrixMetricsPoller
* [Pull 457](https://github.com/Netflix/Hystrix/pull/457) Fixing resettability of HystrixMetricsPublisherFactory
* [Pull 451](https://github.com/Netflix/Hystrix/pull/451) Removed ExceptionThreadingUtility
* [Pull 366](https://github.com/Netflix/Hystrix/pull/366) Added support to get command start time in Nanos
* [Pull 456](https://github.com/Netflix/Hystrix/pull/456) Allow hooks to generate HystrixBadRequestExceptions that get handled appropriately
* [Pull 454](https://github.com/Netflix/Hystrix/pull/454) Add tests around HystrixRequestLog in HystrixObservableCommand
* [Pull 453](https://github.com/Netflix/Hystrix/pull/453) Resettable command and thread pool defaults
* [Pull 452](https://github.com/Netflix/Hystrix/pull/452) Make HystrixPlugins resettable
* [Pull 450](https://github.com/Netflix/Hystrix/pull/450) Add fallback tests to HystrixObservableCommand
* [Pull 449](https://github.com/Netflix/Hystrix/pull/449) Move thread completion bookkeeping to end of chain
* [Pull 447](https://github.com/Netflix/Hystrix/pull/447) Synchronous queue fix
* [Pull 376](https://github.com/Netflix/Hystrix/pull/376) Javanica README cleanup
* [Pull 378](https://github.com/Netflix/Hystrix/pull/378) Exection hook call sequences (based on work submitted in [Pull 327](https://github.com/Netflix/Hystrix/pull/327))
* [Pull 374](https://github.com/Netflix/Hystrix/pull/374) RequestBatch logging
* [Pull 371](https://github.com/Netflix/Hystrix/pull/371) Defer creation of IllegalStateException in collapser flow (based on work submitted in [Pull 264](https://github.com/Netflix/Hystrix/pull/264))
* [Pull 369](https://github.com/Netflix/Hystrix/pull/369) Added basic auth to Hystrix Dashboard (based on work submitted in [Pull 336](https://github.com/Netflix/Hystrix/pull/336))
* [Pull 367](https://github.com/Netflix/Hystrix/pull/367) Added thread pool metrics back to execution flow (based on test submitted in [Pull 339](https://github.com/Netflix/Hystrix/pull/339))
* [Pull 365](https://github.com/Netflix/Hystrix/pull/365) Fix Javadoc for HystrixCommand.Setter
* [Pull 364](https://github.com/Netflix/Hystrix/pull/364) Upgrade servo to 0.7.5
* [Pull 362](https://github.com/Netflix/Hystrix/pull/362) Fixed hystrix-rxnetty-metrics-stream unit tests
* [Pull 359](https://github.com/Netflix/Hystrix/pull/359) Fixed Javanica unit tests
* [Pull 361](https://github.com/Netflix/Hystrix/pull/361) Race condition when creating HystrixThreadPool (initially submitted as [Pull 270](https://github.com/Netflix/Hystrix/pull/270))
* [Pull 358](https://github.com/Netflix/Hystrix/pull/358) Fixed Clojure unit tests that failed with RxJava 1.0
* [Commit 2edcd5](https://github.com/Netflix/Hystrix/commit/2edcd578194849a1c2f5acd73a2e6f10ebfdd112) Upgrade to core-metrics 3.0.2
* [Pull 310](https://github.com/Netflix/Hystrix/pull/310) Osgi-ify hystrix-core, hystrix-examples
* [Pull 340](https://github.com/Netflix/Hystrix/pull/340) Race condition when creating HystrixThreadPool
* [Pull 343](https://github.com/Netflix/Hystrix/pull/343) Added 3 new constructors for common command setup
* [Pull 347](https://github.com/Netflix/Hystrix/pull/347) Javanica: Allow for @HystrixCommand to be used on parameterized return type
* [Pull 353](https://github.com/Netflix/Hystrix/pull/353) Fixing a hystrix-examples compilation failure
* [Pull 338](https://github.com/Netflix/Hystrix/pull/338) API Changes after design review of [Issue 321](https://github.com/Netflix/Hystrix/issues/321)
* [Pull 344](https://github.com/Netflix/Hystrix/pull/344) Upgrade to Gradle 1.12 
* [Pull 334](https://github.com/Netflix/Hystrix/pull/334) Javanica: Hystrix Error Propagation 
* [Pull 326](https://github.com/Netflix/Hystrix/pull/326) Javanica: Added support for setting threadPoolProperties through @HystrixCommand annotation
* [Pull 318](https://github.com/Netflix/Hystrix/pull/318) HystrixAsyncCommand and HystrixObservableCommand
* [Pull 316](https://github.com/Netflix/Hystrix/pull/316) Add support for execution.isolation.semaphore.timeoutInMilliseconds

### Version 1.3.20 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.20%22)) ###
* [Pull 533] (https://github.com/Netflix/Hystrix/pull/533) Upgrade to RxJava 1.0.4
* [Pull 528] (https://github.com/Netflix/Hystrix/pull/528) Pass RuntimeException to onError hook in semaphore-rejection and short-circuit cases, instead of null
* [Pull 520] (https://github.com/Netflix/Hystrix/pull/520) More unit tests for hook ordering
* [Commit 61b77c] (https://github.com/Netflix/Hystrix/commit/61b77c305bda6dbd4dc8c86445b4a6670f981845) Fix flow where ExecutionHook.onComplete was called twice
* [Commit a5e52a] (https://github.com/Netflix/Hystrix/commit/a5e52a6d29cd911c1e14ec107a875a9343472db5) Add call to ExecutionHook.onError in HystrixBadRequestException flow
* [Commit cec25e] (https://github.com/Netflix/Hystrix/commit/cec25ed7c6f10c4c59189b443bda844fa39043d6) Fix hook ordering assertions
* [Pull 508] (https://github.com/Netflix/Hystrix/pull/508) Backport of [Pull 327] (https://github.com/Netflix/Hystrix/pull/327) from master: Add hook assertions to unit tests
* [Pull 507] (https://github.com/Netflix/Hystrix/pull/507) Fix hystrix-clj unit tests
* [Commit 62be49] (https://github.com/Netflix/Hystrix/commit/62be49465ae418509814fd195081ef7611eb6015) RxJava 1.0.2

### Version 1.3.19 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.19%22)) ###

* [Pull 348](https://github.com/Netflix/Hystrix/pull/348) Javanica: Allow for @HystrixCommand to be used on parameterized return type
* [Pull 329](https://github.com/Netflix/Hystrix/pull/329) Javanica: allowing configuration of threadPoolProperties through @HystrixCommand annotation

### Version 1.4.0 Release Candidate 5 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.0-RC5%22)) ###

_NOTE: This code is believed to be production worthy but is still a "Release Candidate" until [these possible functional or design issues are resolved](https://github.com/Netflix/Hystrix/issues?q=is%3Aopen+is%3Aissue+milestone%3A1.4)._

* [Pull 314](https://github.com/Netflix/Hystrix/pull/314) RxJava 0.20 and Remove Deprecated Usage
* [Pull 307](https://github.com/Netflix/Hystrix/pull/307) Dashboard: Avoid NPE when 'origin' parameter not present

### Version 1.3.18 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.18%22)) ###

* [Pull 305](https://github.com/Netflix/Hystrix/pull/305) Removing deprecated RxJava usage

### Version 1.3.17 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.3.17%22)) ###

* [Pull 291](https://github.com/Netflix/Hystrix/pull/291) String optimization for HystrixRequestLog
* [Pull 296](https://github.com/Netflix/Hystrix/pull/296) Fix Premature Unsubscribe Bug
* [Commit 47122e](https://github.com/Netflix/Hystrix/commit/1268454ec4381b6ae121cac1675205484847122e) RxJava 0.20.1

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
