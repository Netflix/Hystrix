# Hystrix Releases #

### Version 1.5.13 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.13%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.13/)) ###

- [Pull 1621](https://github.com/Netflix/Hystrix/pull/1621) Fixed bug where an unsubscription of a command in half-open state leaves circuit permanently open
- [Pull 1605](https://github.com/Netflix/Hystrix/pull/1605) Change return value on unsubscribe to Observable.empty().  Thanks @atoulme !
- [Pull 1615](https://github.com/Netflix/Hystrix/pull/1615) Updated Gradle to version 4.0. Thanks @wlsc !
- [Pull 1616](https://github.com/Netflix/Hystrix/pull/1616) Javanica: Wrong hystrix event type for fallback missing.  Thanks @dmgcodevil  !
- [Pull 1606](https://github.com/Netflix/Hystrix/pull/1606) Escape user entered input to avoid HTML injection. This fixes #1456.  Thanks @atoulme  !
- [Pull 1595](https://github.com/Netflix/Hystrix/pull/1595) Possibility to add custom root node for command and thread pool metrics.  Thanks @dstoklosa !
- [Pull 1587](https://github.com/Netflix/Hystrix/pull/1587) Throw IllegalStateException if request cache is not available when clearing.  Thanks @jack-kerouac !

### Version 1.5.12 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.12%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.12/)) ###

- [Pull 1586](https://github.com/Netflix/Hystrix/pull/1586) Start streams for CodaHale metric consumer, ot get it actually working
- [Pull 1584](https://github.com/Netflix/Hystrix/pull/1584) Javanica: Wire up allowMaximumSizeToDivergeFromCoreSize thread-pool property
- [Pull 1585](https://github.com/Netflix/Hystrix/pull/1585) Fix actualMaximumSize Codahale threadpool metric calculation
- [Pull 1567](https://github.com/Netflix/Hystrix/pull/1567) Fix interaction between ExceptionNotWrappedInHystrix and HystrixBadRequestException.  Thanks @gagoman !
- [Pull 1576](https://github.com/Netflix/Hystrix/pull/1576) Fix permyriad calculation for 99.9p latency
- [Pull 1524](https://github.com/Netflix/Hystrix/pull/1524) Javanica: Support rx.Single or rx.Completable types.  Thanks @dmgcodevil !
- [Pull 1574](https://github.com/Netflix/Hystrix/pull/1574) Add unit-test for using a Completable in a HystrixObservableCommand
- [Pull 1572](https://github.com/Netflix/Hystrix/pull/1572) Javanica: Wire up maximumSize thread-pool property.  Thanks @dmgcodevil !
- [Pull 1573](https://github.com/Netflix/Hystrix/pull/1573) Javanica: Don't get cause from HystrixBadRequestException if null.  Thanks @dmgcodevil !
- [Pull 1570](https://github.com/Netflix/Hystrix/pull/1570) Only create HystrixContextRunnable in timeout case lazily, when timeout is fired
- [Pull 1568](https://github.com/Netflix/Hystrix/pull/1568) Made circuit-opening happen in background thread, powered by metric streams
- [Pull 1561](https://github.com/Netflix/Hystrix/pull/1561) Add error-handling for unexpected errors to servlet writes in metric sample servlet
- [Pull 1556](https://github.com/Netflix/Hystrix/pull/1556) Typo fix in Javanica fallback log. Thanks @Thunderforge !
- [Pull 1551](https://github.com/Netflix/Hystrix/pull/1551) Match colors in multiple circuit-breaker status case.  Thanks @eunmin !
- [Pull 1547](https://github.com/Netflix/Hystrix/pull/1547) Support multiple circuit-breaker statuses in dashboard against aggregated data.  Thanks @eunmin !
- [Pull 1539](https://github.com/Netflix/Hystrix/pull/1539) Fix unintentionally shared variable in hystrix-metrics-event-stream-jaxrs.  Thanks @justinjose28 !
- [Pull 1535](https://github.com/Netflix/Hystrix/pull/1535) Move markCommandExecution after markEvent SUCCESS to allow eventNotifier to have full context of execution.  Thanks @bltb!

### Version 1.5.11 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.11%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.11/)) ###

- [Pull 1531](https://github.com/Netflix/Hystrix/pull/1531) Add assertion to dashboard receiving metrics data.  Thanks @lholmquist !
- [Pull 1529](https://github.com/Netflix/Hystrix/pull/1529) Remove commons-collection as dependency of hystrix-javanica.  Thanks @Psynbiotik !
- [Pull 1532](https://github.com/Netflix/Hystrix/pull/1532) Move metrics subscription out of synchronized block in HealthCountsStream to limit time spent holding a lock.
- [Pull 1513](https://github.com/Netflix/Hystrix/pull/1513) Fixed COMMAND_MAX_ACTIVE metrics for Coda Hale metrics.  Thanks @chrisgray !
- [Pull 1515](https://github.com/Netflix/Hystrix/pull/1515) Fixed comment typo in HystrixCommand.  Thanks @kmkr !
- [Pull 1512](https://github.com/Netflix/Hystrix/pull/1512) Upgrade codahale metrics-core to 3.2.2.  Thanks @chrisgray !
- [Pull 1507](https://github.com/Netflix/Hystrix/pull/1507) README typo fix. Thanks @PiperChester !
- [Pull 1503](https://github.com/Netflix/Hystrix/pull/1503) Allow BadRequest exceptions to not be attached if user explicitly wants unwrapped exceptions.  Thanks @mNantern !
- [Pull 1502](https://github.com/Netflix/Hystrix/pull/1502) Remove useless code in HystrixConcurrencyStrategy.  Thanks @zzzvvvxxxd !
- [Pull 1495](https://github.com/Netflix/Hystrix/pull/1495) Add hystrix-metrics-event-stream-jaxrs.  Thanks @justinjose28 !
- [Pull 1498](https://github.com/Netflix/Hystrix/pull/1498) Upgrade Servo to 0.10.1 in hystrix-servo-metrics-publisher

### Version 1.5.10 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.10%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.10/)) ###

- [Pull 1489](https://github.com/Netflix/Hystrix/pull/1489) Added rollingMaxConcurrentExecutionCount to CodaHale metrics publisher.  Thanks @LuboVarga !
- [Pull 1481](https://github.com/Netflix/Hystrix/pull/1481) Add sanity checking to HystrixCommandAspect to debug unreproducible cases.  Thanks @dmgcodevil !
- [Pull 1482](https://github.com/Netflix/Hystrix/pull/1482) Make it possible to re-use fallback methods in Javanica.  (Addresses #1446).  Thanks @dmgcodevil !
- [Pull 1488](https://github.com/Netflix/Hystrix/pull/1488) Fix spelling mistakes in Javanica docs.  Thanks @bltb!
- [Pull 1475](https://github.com/Netflix/Hystrix/pull/1475) Added example usage of CodaHale metrics publisher.  Thanks @LuboVarga !
- [Pull 1469](https://github.com/Netflix/Hystrix/pull/1469) Fix possible concurrency bug.  Thanks @petercla! 
- [Pull 1453](https://github.com/Netflix/Hystrix/pull/1453) Add Javanica unit test for NotWrapped checked exception.  Thanks @tbvh!

### Version 1.5.9 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.9%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.9/)) ###

* [Pull 1423](https://github.com/Netflix/Hystrix/pull/1423) Write correct value to error log when maximumSize < coreSize.  Thanks @diver-in-sky!
* [Pull 1412](https://github.com/Netflix/Hystrix/pull/1412) Javanica: raiseHystrixExceptions support for Observables.  Thanks @michaelcowan !
* [Pull 1441](https://github.com/Netflix/Hystrix/pull/1441) Use Gretty Gradle plugin for hystrix-examples-webapp 
* [Pull 1442](https://github.com/Netflix/Hystrix/pull/1442) Fix handling of client-connect/disconnect never getting released if it occurs before metrics start getting produced by hystrix-metrics-event-stream.  Thanks for review, @mattnelson!
* [Pull 1444](https://github.com/Netflix/Hystrix/pull/1444) More efficient server thread release in hystrix-metrics-event-stream.  Thanks @mattnelson for the suggestion!
* [Pull 1443](https://github.com/Netflix/Hystrix/pull/1443) Use Gretty Gradle plugin for hystrix-dashboard
* [Pull 1445](https://github.com/Netflix/Hystrix/pull/1445) Add missing onUnsubscribe hook to execution hooks
* [Pull 1414](https://github.com/Netflix/Hystrix/pull/1414) Introduce NotWrappedByHystrix exception type to indicate Hystrix should propagate it back without wrapping in a HystrixRuntimeException.  Thanks @tbvh!
* [Pull 1448](https://github.com/Netflix/Hystrix/pull/1448) Remove dependency on jackson-cbor in hystrix-serialization.  This belongs in a different module.  Existing public methods now throw an exception.
* [Pull 1435](https://github.com/Netflix/Hystrix/pull/1435) Allow the property `allowMaximumSixeToDivergeFromCoreSize` to be set dynamically.  Thanks @ptab!
* [Pull 1447](https://github.com/Netflix/Hystrix/pull/1447) Allow the property `allowMaximumSixeToDivergeFromCoreSize` to be set dynamically. 
* [Pull 1449](https://github.com/Netflix/Hystrix/pull/1435) Introduce a distinction between `maximumSize` (configured value) and `actualMaximumSize` (value used to set the thread pool maximum size).  Publish both values to make understanding configuration more straightforward.  Thanks @ptab!

### Version 1.5.8 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.8%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.8/)) ###

* [Pull 1419](https://github.com/Netflix/Hystrix/pull/1419) When user has not opted in to letting core/maximum threadpools diverge, ensure dynamic updates to coreSize apply to both
* [Pull 1415](https://github.com/Netflix/Hystrix/pull/1415) Fix spelling mistake in comments.  Thanks @starlight36 !

### Version 1.5.7 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.7%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.7/)) ###

* [Pull 1408](https://github.com/Netflix/Hystrix/pull/1408) Fix Clojure key name for collapsing.  Thanks @crimeminister !
* [Pull 1407](https://github.com/Netflix/Hystrix/pull/1407) Reset percentile snapshot whenever all HystrixRollingPercentile buckets are empty
* [Pull 1397](https://github.com/Netflix/Hystrix/pull/1397) Javanica: Add option to raise HystrixRuntimeException
* [Pull 1399](https://github.com/Netflix/Hystrix/pull/1399) Add configuration to make users opt-in to allowing coreSize and maximumSize to diverge.  See config [here] (https://github.com/Netflix/Hystrix/wiki/Configuration#allowMaximumSizeToDivergeFromCoreSize)
* [Pull 1396](https://github.com/Netflix/Hystrix/pull/1396) If command is unsubscribed before any work is done, return Observable.empty().  
* [Pull 1393](https://github.com/Netflix/Hystrix/pull/1393) Javanica: Performance improvement by caching weavingMode boolean.  Thanks @ricardoletgo !
* [Pull 1389](https://github.com/Netflix/Hystrix/pull/1389) Javanica: Send fallback exception to client instead of primary command.  Thanks @dmgcodevil !
* [Pull 1385](https://github.com/Netflix/Hystrix/pull/1385) Bump jmh Gradle plugin to 0.3.1.  Thanks @monkey-mas!
* [Pull 1382](https://github.com/Netflix/Hystrix/pull/1382) Bump jmh to 1.15.  Thanks @monkey-mas!
* [Pull 1380](https://github.com/Netflix/Hystrix/pull/1380) Add jmh test for open-circuit case
* [Pull 1376](https://github.com/Netflix/Hystrix/pull/1376) Clean up documentation around thread keep-alive.  Thanks @bitb !
* [Pull 1375](https://github.com/Netflix/Hystrix/pull/1375) Remove cancelled tasks from threadpool queue
* [Pull 1371](https://github.com/Netflix/Hystrix/pull/1371) Allow core and maximum size of threadpools to diverge.

### Version 1.5.6 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.6%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.6/)) ###

* [Pull 1368](https://github.com/Netflix/Hystrix/pull/1368) Upgrade jmh to 1.14.1
* [Pull 1365](https://github.com/Netflix/Hystrix/pull/1365) Upgrade to Gradle 3.1 / Nebula 3.4.0
* [Pull 1364](https://github.com/Netflix/Hystrix/pull/1364) Fix backwards-incompatibility introduced in #1356
* [Pull 1363](https://github.com/Netflix/Hystrix/pull/1363) Fix metrics regression where thread pool objects without any executions were being sent out in metrics streams
* [Pull 1360](https://github.com/Netflix/Hystrix/pull/1360) Convert command-construction jmh test to single-shot
* [Pull 1356](https://github.com/Netflix/Hystrix/pull/1356) Add better AppEngine detection mechanism that allows GAE-Flexible to work like any other JVM.  Thanks @cadef!
* [Pull 1353](https://github.com/Netflix/Hystrix/pull/1353) Upgrade to RxJava 1.2.0
* [Pull 1351](https://github.com/Netflix/Hystrix/pull/1351) Remove histogram object-pooling
* [Pull 1336](https://github.com/Netflix/Hystrix/pull/1336) Overall Dashboard UX improvements.  Thanks @kennedyoliveira !
* [Pull 1320](https://github.com/Netflix/Hystrix/pull/1320) Adding example of HystrixObservableCollapser.  Thanks @zsoltm !
* [Pull 1341](https://github.com/Netflix/Hystrix/pull/1341) Javanica fix for handling commands with generic types.  Thanks @dmgcodevil ! 
* [Pull 1340](https://github.com/Netflix/Hystrix/pull/1340) Refactor how commands determine if fallbacks are user-defined

### Version 1.5.5 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.5%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.5/)) ###

* [Pull 1323](https://github.com/Netflix/Hystrix/pull/1323) Remove ReactiveSocket modules and change Jenkins release process back to JDK7

### Version 1.5.4 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.4%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.4/)) ###

* [Pull 1308](https://github.com/Netflix/Hystrix/pull/1308) Update jmh to 1.13
* [Pull 1310](https://github.com/Netflix/Hystrix/pull/1310) Update nebula.netflixoss Gradle plugin to 3.3.0
* [Pull 1309](https://github.com/Netflix/Hystrix/pull/1309) Update HdrHistogram to 2.1.9
* [Pull 1307](https://github.com/Netflix/Hystrix/pull/1307) Update RxJava to 1.1.8
* [Pull 1306](https://github.com/Netflix/Hystrix/pull/1306) Update Clojure to 1.7.0 and nebula-clojure-plugin to 4.0.1
* [Pull 1305](https://github.com/Netflix/Hystrix/pull/1305) Update Gradle to 2.14
* [Pull 1304](https://github.com/Netflix/Hystrix/pull/1304) Make all metrics streams multicast.
* [Pull 1303](https://github.com/Netflix/Hystrix/pull/1303) Update RxNetty to 0.4.17
* [Pull 1302](https://github.com/Netflix/Hystrix/pull/1302) Manual merge of #1265.  Interrupt execution thread on HystrixCommand#queue()#cancel(true).  Thanks @mmanciop!
* [Pull 1300](https://github.com/Netflix/Hystrix/pull/1300) Update all completion state for scalar command in the onNext handling
* [Pull 1294](https://github.com/Netflix/Hystrix/pull/1294) Make sure that threadpools shutdown when asked to.  Thanks @thesmith!
* [Pull 1297](https://github.com/Netflix/Hystrix/pull/1297) Fix typo in README.  Thanks @ManishMaheshwari!
* [Pull 1295](https://github.com/Netflix/Hystrix/pull/1295) Fix typo in README.  Thanks @C-Otto!
* [Pull 1273](https://github.com/Netflix/Hystrix/pull/1273) Corrected ignoreExceptions for Observable-returning methods.  Thanks @jbojar!
* [Pull 1197](https://github.com/Netflix/Hystrix/pull/1197) Eureka integration for Hystrix dashboard.  Thanks @diegopacheco!
* [Pull 1278](https://github.com/Netflix/Hystrix/pull/1278) Prevent duplicate arguments from getting into a single collapser RequestBatch
* [Pull 1277](https://github.com/Netflix/Hystrix/pull/1277) Make HystrixCollapser.toObservable lazy
* [Pull 1276](https://github.com/Netflix/Hystrix/pull/1276) Make HystrixObservableCollapser.toObservable lazy
* [Pull 1271](https://github.com/Netflix/Hystrix/pull/1271) Fix race condition in all of the Hystrix*Key.asKey methods.  Thanks @daniilguit!
* [Pull 1274](https://github.com/Netflix/Hystrix/pull/1274) Make AbstractCommand.toObservable lazy
* [Pull 1270](https://github.com/Netflix/Hystrix/pull/1270) Fix deprecation warnings by upgrading RxJava and Netty usages
* [Pull 1269](https://github.com/Netflix/Hystrix/pull/1269) Rework hystrix-data-stream module to just include serialization logic
* [Pull 1259](https://github.com/Netflix/Hystrix/pull/1259) Javanica: added DefaultProperties annotation.  Thanks @dmgcodevil!
* [Pull 1261](https://github.com/Netflix/Hystrix/pull/1261) Add toString() to key implementations.  Thanks @mebigfatguy!
* [Pull 1258](https://github.com/Netflix/Hystrix/pull/1258) Javanica: Change getMethod to recursively search in parent types.  Thanks @dmgcodevil!
* [Pull 1255](https://github.com/Netflix/Hystrix/pull/1255) Introduce intermediate data streams module [LATER REVERTED - SEE #1269 ABOVE]
* [Pull 1254](https://github.com/Netflix/Hystrix/pull/1254) Allow multiple consumers of sample data to only trigger work once and share data
* [Pull 1251](https://github.com/Netflix/Hystrix/pull/1251) Fix Dashboard RPS
* [Pull 1247](https://github.com/Netflix/Hystrix/pull/1247) Call thread pool size setters only when pool size changes.  Thanks @yanglifan!
* [Pull 1246](https://github.com/Netflix/Hystrix/pull/1246) Move HystrixDashboardStream to hystrix-core
* [Pull 1244](https://github.com/Netflix/Hystrix/pull/1244) Fix handling of invalid weavingMode property.  Thanks @mebigfatguy!
* [Pull 1238](https://github.com/Netflix/Hystrix/pull/1238) Local variable caching fixups.  Thanks @mebigfatguy!
* [Pull 1236](https://github.com/Netflix/Hystrix/pull/1236) ReactiveSocket metrics client/server
* [Pull 1235](https://github.com/Netflix/Hystrix/pull/1235) Add demo that composes async command executions
* [Pull 1231](https://github.com/Netflix/Hystrix/pull/1231) Make inner classes static where possible, and remove outer class reference.  Thanks @mebigfatguy!
* [Pull 1229](https://github.com/Netflix/Hystrix/pull/1229) Remove dead Setter class from RequestCollapserFactory. Thanks @mebigfatguy!
* [Pull 1225](https://github.com/Netflix/Hystrix/pull/1225) Remove unused thunk from HystrixCommandMetrics.  Thanks @mebigfatguy!
* [Pull 1224](https://github.com/Netflix/Hystrix/pull/1224) Remove dead field from RequestCollapserFactory.  Thanks @mebigfatguy!
* [Pull 1221](https://github.com/Netflix/Hystrix/pull/1221) Improve grammar in comments and log messages.  Thanks @dysmento !
* [Pull 1211](https://github.com/Netflix/Hystrix/pull/1211) Add ReactiveSocket metrics stream.  Thanks @robertroeser!
* [Pull 1219](https://github.com/Netflix/Hystrix/pull/1219) Move map lookup outside of loop in collapser code.  Thanks @mebigfatguy!

### Version 1.5.3 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.3%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.3/)) ###

The largest new feature in this release is that cancellation is now supported.  Either calling `cancel()` on the result of `HystrixCommand.queue()` or `unsubscribe()` on the result of `HystrixCommand.toObservable().subscribe()` will propagate the cancellation to the underlying work.

* [Pull 1218](https://github.com/Netflix/Hystrix/pull/1218) Fixing unsubscription races by modeling explicit FSMs for command and thread execution state
* [Pull 1217](https://github.com/Netflix/Hystrix/pull/1217) Remove dead code in RequestEventsStream.  Thanks @mebigfatguy!
* [Pull 1214](https://github.com/Netflix/Hystrix/pull/1214) Fix Servo metric calculation.  Bug identified by @AchimWe.
* [Pull 1213](https://github.com/Netflix/Hystrix/pull/1213) Use parameterized logging.  Thanks @mebigfatguy!
* [Pull 1212](https://github.com/Netflix/Hystrix/pull/1212) Correct logging contexts.  Thanks @mebigfatguy!
* [Pull 1210](https://github.com/Netflix/Hystrix/pull/1210) Javanica: code optimization to fetch parameters only if needed.  Thanks @mebigfatguy!
* [Pull 1209](https://github.com/Netflix/Hystrix/pull/1209) Fixed thread-state cleanup to happen on unsubscribe or terminate
* [Pull 1208](https://github.com/Netflix/Hystrix/pull/1208) Reorganization of HystrixCollapser/HystrixObservableCollapser logic to support cancellation
* [Pull 1207](https://github.com/Netflix/Hystrix/pull/1207) Fix command concurrency metric in light of cancellation
* [Pull 1206](https://github.com/Netflix/Hystrix/pull/1206) Added subscribeOn to HystrixObservableCommand in JMH test to make it async
* [Pull 1204](https://github.com/Netflix/Hystrix/pull/1204) Reorganization of AbstractCommand logic to support cancellation
* [Pull 1198](https://github.com/Netflix/Hystrix/pull/1198) Release semaphores upon cancellation.
* [Pull 1194](https://github.com/Netflix/Hystrix/pull/1194) Improved tests readability a bit by letting exceptions propagate out.  Thanks @caarlos0!
* [Pull 1193](https://github.com/Netflix/Hystrix/pull/1193) More tests using HystrixRequestContext rule.  Thanks @caarlos0!
* [Pull 1181](https://github.com/Netflix/Hystrix/pull/1181) Deprecate getter and setter for unused collapsingEnabled property in Collapser Setter.  Thanks @nluchs!
* [Pull 1184](https://github.com/Netflix/Hystrix/pull/1184) migrating all hystrix-javanica tests to hystrix-junit.  Thanks @caarlos0!
* [Pull 1147](https://github.com/Netflix/Hystrix/pull/1147) Added hystrix-junit.  Thanks @caarlos0!

### Version 1.4.26 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.26%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.26/)) ###

* [Pull 1169](https://github.com/Netflix/Hystrix/pull/1169) Javanica: Switch hystrix-javanica to use getExecutionException, which returns Exception object even when command is not executed

### Version 1.5.2 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.2%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.2/)) ###

* [Pull 1171](https://github.com/Netflix/Hystrix/pull/1171) Do all histogram latency summarization upfront to minimize storage/operations on them
* [Pull 1167](https://github.com/Netflix/Hystrix/pull/1167) Javanica: Switch hystrix-javanica to use getExecutionException, which returns Exception object even when command is not executed
* [Pull 1157](https://github.com/Netflix/Hystrix/pull/1157) Make HystrixMetricsPoller a daemon thread
* [Pull 1154](https://github.com/Netflix/Hystrix/pull/1154) Remove more unused methods
* [Pull 1151](https://github.com/Netflix/Hystrix/pull/1151) Remove unused method in HystrixCollapserProperties
* [Pull 1149](https://github.com/Netflix/Hystrix/pull/1149) Make queue size of MetricJsonListener configurable
* [Pull 1124](https://github.com/Netflix/Hystrix/pull/1124) Turning down loglevels of metrics streams
* [Pull 1120](https://github.com/Netflix/Hystrix/pull/1120) Making the HystrixTimeoutException instance per-command, not static

### Version 1.4.25 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.25%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.25/)) ###

* [Pull 1114](https://github.com/Netflix/Hystrix/pull/1114) Make queue size of MetricsJsonListener configurable
* [Pull 1121](https://github.com/Netflix/Hystrix/pull/1121) HystrixTimeoutException is non-static for better stacktrace

Artifacts: [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.25%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.25/)

### Version 1.5.1 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.1%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.1/)) ###

* [Pull 1118](https://github.com/Netflix/Hystrix/pull/1118) Revert #1075.  Return userThreadLatency to metrics, mostly to maintain format compatibility with data streams from 1.4.x
* [Pull 1116](https://github.com/Netflix/Hystrix/pull/1116) Fix references to underscore.js over HTTPS
* [Pull 1115](https://github.com/Netflix/Hystrix/pull/1115) Fix LICENSE reference in README
* [Pull 1111](https://github.com/Netflix/Hystrix/pull/1111) HystrixRequestContext implements Closeable

### Version 1.5.0 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.0%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.0/)) ###

The general premise of this release is to make metrics more flexible within Hystrix. See https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring for a deep dive on the new metrics architecture.  The high-level approach is to model metrics directly as a stream, so that Hystrix metrics consumers may aggregate the metrics as they wish. In 1.4.x and prior releases, `HystrixRollingNumber` and `HystrixRollingPercentile` were used to store aggregate command counters and command latencies, respectively.  These are no longer used.  

Instead, new concepts like `HystrixCommandCompletionStream` are present.  These may be consumed by a rolling, summarizing data structure (like `HystrixRollingNumber`), or they may be consumed without any aggregation at all.  This should allow for all metrics processing to move off-box, if you desire to add that piece to your infrastructure.

This version should be backwards-compatible with v1.4.x.  If you find otherwise, please submit a Hystrix issue as it was unintentional.

This version also introduces new metric streams: ([configuration](https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#configuration-stream) and [Utilization](https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#utilization-stream)) have been added, along with a [request-scoped stream](https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#request-streams).

Archaius is now a soft-dependency of Hystrix, so you can supply your own configuration mechanism.

Some known semantic changes:
* Latencies for timeouts and bad-requests are now included in command latency
* Latency distribution percentiles are now calculated with HdrHistogram library and don't have a max number of elements in the distribution
* Previously, HealthCounts data allowed reads to see the value in the "hot" bucket.  (the one currently being written to).  That does not happen anymore - only full read-only buckets are available for reads.
* Bucket rolling now happens via Rx background threads instead of unlucky Hystrix command threads.  This makes command performance more predictable.  User-thread latency is now practically indistinguishable from command latency.

### Version 1.4.24 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.24%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.24/)) ###

* [Pull 1113](https://github.com/Netflix/Hystrix/pull/1113) Make HystrixRequestContext implement Closeable
* [Pull 1112](https://github.com/Netflix/Hystrix/pull/1112) Upgrade to latest Nebula Gradle plugin
* [Pull 1110](https://github.com/Netflix/Hystrix/pull/1110) Upgrade to RxJava 1.1.1
* [Pull 1108](https://github.com/Netflix/Hystrix/pull/1108) Javanica HystrixRequestCacheManager should use the concurrency strategy 

### Version 1.5.0-rc.5 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.0-rc.5%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.0-rc.5/)) ###

This version does not have any known bugs, but is not recommended for production use until 1.5.0.

Included changes: 

* [Pull 1102](https://github.com/Netflix/Hystrix/pull/1102) Bugfix to null check on HystrixRequestCache context

### Version 1.5.0-rc.4 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.0-rc.4%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.0-rc.4/)) ###

This version does not have any known bugs, but is not recommended for production use until 1.5.0.

Included changes: 

* [Pull 1099](https://github.com/Netflix/Hystrix/pull/1099) Bugfix to get Hystrix dashboard operational again

### Version 1.5.0-rc.3 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.0-rc.3%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.0-rc.3/)) ###

This version does not have any known bugs, but is not recommended for production use until 1.5.0.

A few dependency bumps, but the major change here is that Archaius is now a soft dependency of hystrix-core.  Thanks to @agentgt for the PR!. Thanks also to @caarlos0 for the NPE fix in HystrixRequestCache.
 
Included changes: 

* [Pull 1079](https://github.com/Netflix/Hystrix/pull/1079) Remove dynamic config lookup in HystrixThreadPool
* [Pull 1081](https://github.com/Netflix/Hystrix/pull/1081) Cleanup hystrix-javanica BadRequest docs
* [Pull 1093](https://github.com/Netflix/Hystrix/pull/1093) Fix NPE in HystrixRequestCache when HystrixRequestContext not initialized
* [Pull 1083](https://github.com/Netflix/Hystrix/pull/1083) Made Archaius a soft dependency of hystrix-core.  It is now possible to run without Archaius and rely on j.u.l.ServiceLoader or system properties only
* [Pull 1095](https://github.com/Netflix/Hystrix/pull/1095) Upgrade to Nebula netflixoss 3.2.3
* [Pull 1096](https://github.com/Netflix/Hystrix/pull/1096) Upgrade to RxJava 1.1.1
* [Pull 1097](https://github.com/Netflix/Hystrix/pull/1097) Fix POM generation by excluding WAR artifacts

### Version 1.5.0-rc.2 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.0-rc.2%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.0-rc.2/)) ###

This version does not have any known bugs, but is not recommended for production use until 1.5.0.

This is mostly a new set of features building on top of Release Candidate 1.  Specifically, some sample streams ([Configuration](https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#configuration-stream) and [Utilization](https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#utilization-stream)) have been added, along with a [request-scoped stream](https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#request-streams).
 
Included changes: 

* [Pull 1050](https://github.com/Netflix/Hystrix/pull/1050) Modular command construction
* [Pull 1061](https://github.com/Netflix/Hystrix/pull/1061) Sample config/utilization streams, and request-scoped streams
* [Pull 1064](https://github.com/Netflix/Hystrix/pull/1064) Safer enum references in case mismatched Hystrix jars are deployed together
* [Pull 1066](https://github.com/Netflix/Hystrix/pull/1066) Layer of abstraction on top of ThreadFactory, so AppEngine can run Hystrix
* [Pull 1067](https://github.com/Netflix/Hystrix/pull/1067) Decouple sample stream JSON from servlets
* [Pull 1067](https://github.com/Netflix/Hystrix/pull/1068) Decouple request-scoped stream JSON from servlets
* [Pull 1075](https://github.com/Netflix/Hystrix/pull/1075) Deprecate userThreadLatency, since it is practically identical to executionLatency now

### Version 1.5.0-rc.1 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.0-rc.1%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.0-rc.1/)) ###

This version does not have any known bugs, but *is not* recommended for production use until 1.5.0.

The general premise of this release is to make metrics more flexible within Hystrix. See https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring for a deep dive on the new metrics architecture.  The high-level view is to make the metrics primitive a stream instead of an aggregate.  In 1.4.x and prior releases, `HystrixRollingNumber` and `HystrixRollingPercentile` were used to store aggregate command counters and command latencies, respectively.  These are no longer used.  

Instead, new concepts like `HystrixCommandCompletionStream` are present.  These may be consumed by a rolling, summarizing data structure (like `HystrixRollingNumber`), or they may be consumed without any aggregation at all.  This should allow for all metrics processing to move off-box, if you desire to add that piece to your infrastructure.

This version should be backwards-compatible with v1.4.x.  If you find otherwise, please submit a Hystrix issue as it was unintentional.

Some known semantic changes:
* Latencies for timeouts and bad-requests are now included in command latency
* Latency distribution percentiles are now calculated with HdrHistogram library and don't have a max number of elements in the distribution
* Previously, HealthCounts data allowed reads to see the value in the "hot" bucket.  (the one currently being written to).  That does not happen anymore - only full read-only buckets are available for reads.
* Bucket rolling now happens via Rx background threads instead of unlucky Hystrix command threads.  This makes command performance more predictable.  User-thread latency is now practically indistinguishable from command latency.

Artifacts: [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.5.0-rc.1%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.5.0-rc.1/)

### Version 1.4.23 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.23%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.23/)) ###

* [Pull 1032](https://github.com/Netflix/Hystrix/pull/1032) Make number of timer threads a piece of config (with Archaius integration)
* [Pull 1045](https://github.com/Netflix/Hystrix/pull/1045) Documentation cleanup in HystrixCommandProperties
* [Pull 1044](https://github.com/Netflix/Hystrix/pull/1044) Add request context and HystrixObservableCommand to command execution JMH tests
* [Pull 1043](https://github.com/Netflix/Hystrix/pull/1043) HystrixObservableCollapser emits error to each submitter when batch command encounters error
* [Pull 1039](https://github.com/Netflix/Hystrix/pull/1039) Use thread-safe data structure for storing list of command keys per-thread
* [Pull 1036](https://github.com/Netflix/Hystrix/pull/1036) Remove redundant ConcurrentHashMap read when getting name from command class
* [Pull 1035](https://github.com/Netflix/Hystrix/pull/1035) Rename command execution JMH tests
* [Pull 1034](https://github.com/Netflix/Hystrix/pull/1034) Remove SHORT_CIRCUITED events from health counts calculation 
* [Pull 1027](https://github.com/Netflix/Hystrix/pull/1027) Fix typo in hystrix-examples-webapp documentation

### Version 1.4.22 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.22%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.22/)) ###

* [Pull 1019](https://github.com/Netflix/Hystrix/pull/1019) hystrix-dashboard: Swap magnifying glass logos
* [Commit 41e9c210f044fe822625acc6e23c5167e56c3f09](https://github.com/Netflix/Hystrix/commit/41e9c210f044fe822625acc6e23c5167e56c3f09) Add OSSMETADATA
* [Pull 1014](https://github.com/Netflix/Hystrix/pull/1014) Upgrade to RxJava 1.1.0
* [Pull 1009](https://github.com/Netflix/Hystrix/pull/1009) hystrix-javanica: Upgrade to AspectJ 1.8.6
* [Pull 1008](https://github.com/Netflix/Hystrix/pull/1008) Add AbstractCommand.getExecutionException
* [Pull 1006](https://github.com/Netflix/Hystrix/pull/1006) Add Cobertura plugin
* [Pull 1005](https://github.com/Netflix/Hystrix/pull/1005) Upgrade RxJava to 1.0.17
* [Pull 1000](https://github.com/Netflix/Hystrix/pull/1000) Fix network-auditor Javadoc
* [Pull 999](https://github.com/Netflix/Hystrix/pull/999) Upgrade Javassist within hystrix-network-auditor-agent
* [Pull 992](https://github.com/Netflix/Hystrix/pull/992) hystrix-dashboard: Remove validation error message when adding a stream
* [Pull 977](https://github.com/Netflix/Hystrix/pull/977) hystrix-javanica support for Observable command

### Version 1.4.21 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.21%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.21/)) ###

* [Pull 978](https://github.com/Netflix/Hystrix/pull/978) Upgrade commons-collections to 3.2.2
* [Pull 959](https://github.com/Netflix/Hystrix/pull/959) Support multiple metric streams in hystrix-dashboard
* [Pull 976](https://github.com/Netflix/Hystrix/pull/976) Prevent execution observable from running when hook throws an error in onXXXStart 
* [Pull 972](https://github.com/Netflix/Hystrix/pull/972) Mark servlet-api dependency as 'provided'
* [Pull 968](https://github.com/Netflix/Hystrix/pull/968) Add defaultSetter() to properties classes to workaround GROOVY-6286

### Version 1.4.20 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.20%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.20/)) ###

* [Pull 965](https://github.com/Netflix/Hystrix/pull/965) Upgrade Nebula Gradle plugin
* [Pull 962](https://github.com/Netflix/Hystrix/pull/962) Javanica: Support for async commands
* [Pull 960](https://github.com/Netflix/Hystrix/pull/960) Avoid Clojure reflection in hystrix-clj
* [Pull 957](https://github.com/Netflix/Hystrix/pull/957) Javanica: Fix threadpool properties
* [Pull 956](https://github.com/Netflix/Hystrix/pull/956) Upgrade JMH from 1.10.3 to 1.11.1
* [Pull 945](https://github.com/Netflix/Hystrix/pull/945) Javanica: Compile-time weaving support
* [Pull 952](https://github.com/Netflix/Hystrix/pull/952) Tolerate lack of RequestContext better for custom concurrency strategies
* [Pull 947](https://github.com/Netflix/Hystrix/pull/947) Upgrade RxNetty to 0.4.12 for RxNetty metrics stream
* [Pull 946](https://github.com/Netflix/Hystrix/pull/946) More extension-friendly Yammer metrics publisher
* [Pull 944](https://github.com/Netflix/Hystrix/pull/944) Fix generated POM to include dependencies in 'compile' scope
* [Pull 942](https://github.com/Netflix/Hystrix/pull/942) Fix metrics stream fallbackEmit metric
* [Pull 941](https://github.com/Netflix/Hystrix/pull/941) Add FALLBACK_MISSING event type and metric

### Version 1.4.19 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.19%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.19/)) ###

This version should be the exact same as 1.4.20, but suffered problems during the publishing process.  Please use 1.4.20 instead.

### Version 1.4.18 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.18%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.18/)) ###

* [Pull 934](https://github.com/Netflix/Hystrix/pull/934) Remove duplicate EventSource from dashboard
* [Pull 931](https://github.com/Netflix/Hystrix/pull/931) Make HystrixTimeoutException public
* [Pull 930](https://github.com/Netflix/Hystrix/pull/930) Support collapser metrics in HystrixMetricPublisher implementations
* [Pull 927](https://github.com/Netflix/Hystrix/pull/927) Dashboard fix to isCircuitBreakerOpen

### Version 1.4.17 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.17%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.17/)) ###

* [Pull 924](https://github.com/Netflix/Hystrix/pull/924) Dashboard protection against XSS
* [Pull 923](https://github.com/Netflix/Hystrix/pull/923) Upgrade to RxJava 1.0.14
* [Pull 922](https://github.com/Netflix/Hystrix/pull/922) Add DEBUG tag to Servo rolling counter and made it a GaugeMetric

### Version 1.4.16 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.16%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.16/)) ###

* [Pull 917](https://github.com/Netflix/Hystrix/pull/917) Better version of making servo-metrics-publisher extension-friendly
* [Pull 912](https://github.com/Netflix/Hystrix/pull/912) Only look up if HystrixRequestCache is enabled once per HystrixObservableCollapser-invocation
* [Pull 911](https://github.com/Netflix/Hystrix/pull/911) Unit test for large threadpool/small queue case in command execution
* [Pull 910](https://github.com/Netflix/Hystrix/pull/910) Make servo-metrics-publisher more extension-friendly
* [Pull 905](https://github.com/Netflix/Hystrix/pull/905) HystrixObservableCollapser examples
* [Pull 902](https://github.com/Netflix/Hystrix/pull/902) Cleanup HystrixObservableCollapser unit tests
* [Pull 900](https://github.com/Netflix/Hystrix/pull/900) Remove commons-collections dependency from hystrix-javanica
* [Pull 897](https://github.com/Netflix/Hystrix/pull/897) Fix missing null check in hystrix-javanica HystrixCacheKeyGenerator

### Version 1.4.15 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.15%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.15/)) ###

* [Pull 890](https://github.com/Netflix/Hystrix/pull/890) Allow multiple responses per collapser argument.  No semantic change for HystrixCollapser, but a bugfix to HystrixObservableCollapser
* [Pull 892](https://github.com/Netflix/Hystrix/pull/892) Cache Setter in MultithreadedMetricsPerfTest
* [Pull 891](https://github.com/Netflix/Hystrix/pull/891) Add request context to command JMH tests
* [Pull 889](https://github.com/Netflix/Hystrix/pull/889) Replace subscribe() in RequestBatch with unsafeUnsubscribe()
* [Pull 887](https://github.com/Netflix/Hystrix/pull/887) Only look up if HystrixRequestCache is enabled once per collapser-invocation
* [Pull 885](https://github.com/Netflix/Hystrix/pull/885) Only look up if HystrixRequestCache is enabled once per command-invocation
* [Pull 876](https://github.com/Netflix/Hystrix/pull/876) Report BAD_REQUEST to HystrixRequestLog
* [Pull 861](https://github.com/Netflix/Hystrix/pull/861) Make hystrix-javanica OSGI-compliant
* [Pull 856](https://github.com/Netflix/Hystrix/pull/856) Add missing licenses
* [Pull 855](https://github.com/Netflix/Hystrix/pull/855) Save allocation if using a convenience constructor for HystrixCommand
* [Pull 853](https://github.com/Netflix/Hystrix/pull/853) Run Travis build in a container
* [Pull 848](https://github.com/Netflix/Hystrix/pull/848) Unit tests to demonstrate HystrixRequestLog was not experiencing data races

### Version 1.4.14 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.14%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.14/)) ###

* [Pull 852](https://github.com/Netflix/Hystrix/pull/852) Fix hystrix-clj that was blocking http://dev.clojure.org/jira/browse/CLJ-1232
* [Pull 849](https://github.com/Netflix/Hystrix/pull/849) Unit tests for HystrixCommands that are part of a class hierarchy with other HystrixCommands

### Version 1.4.13 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.13%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.13/)) ###

* [Pull 839](https://github.com/Netflix/Hystrix/pull/839) Fix typo in hystrix-dashboard js
* [Pull 838](https://github.com/Netflix/Hystrix/pull/838) Add back unit tests for Hystrix class and its reset() method in particular
* [Pull 837](https://github.com/Netflix/Hystrix/pull/837) Upgrade to RxJava 1.0.13
* [Pull 830](https://github.com/Netflix/Hystrix/pull/830) Add validation for rollingCountBadRequest in hystrix-dashboard

### Version 1.4.12 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.12%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.12/)) ###

* [Pull 826](https://github.com/Netflix/Hystrix/pull/826) Safely handle negative input for delay parameter to metrics stream servlet
* [Pull 825](https://github.com/Netflix/Hystrix/pull/825) Only check bucket properties at HystrixRollingNumber construction
* [Pull 824](https://github.com/Netflix/Hystrix/pull/824) Only check bucket properties at HystrixRollingPercentile construction
* [Pull 823](https://github.com/Netflix/Hystrix/pull/823) Fix half hidden mean metric in Hystrix Dashboard because of container height
* [Pull 818](https://github.com/Netflix/Hystrix/pull/818) Only check maxQueueSize value at thread pool construction

### Version 1.4.11 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.11%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.11/)) ###

* [Pull 814](https://github.com/Netflix/Hystrix/pull/814) Upgrade to RxJava 1.0.12
* [Pull 813](https://github.com/Netflix/Hystrix/pull/813) Output something when Hystrix falls back on recoverable java.lang.Error
* [Pull 812](https://github.com/Netflix/Hystrix/pull/812) Fixing overload functions in hystrix-clj
* [Pull 808](https://github.com/Netflix/Hystrix/pull/808) Update Hystrix Metrics Stream README

### Version 1.4.10 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.10%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.10/)) ###

* [Pull 804](https://github.com/Netflix/Hystrix/pull/804) Fix memory leak by switching back to AtomicIntegerArray for HystrixRollingPercentile

### Version 1.4.9 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.9%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.9/)) ###

* [Pull 799](https://github.com/Netflix/Hystrix/pull/799) Fix thread-safety of writes to HystrixRollingPercentile

### Version 1.4.8 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.8%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.8/)) ###

* [Pull 797](https://github.com/Netflix/Hystrix/pull/797) Move all histogram reads to a single-threaded path.
* [Pull 794](https://github.com/Netflix/Hystrix/pull/794) Allow dashboard connection on 'Enter' keypress
* [Pull 787](https://github.com/Netflix/Hystrix/pull/787) Reject requests after metrics-event-stream servlet shutdown
* [Pull 785](https://github.com/Netflix/Hystrix/pull/785) Update metrics package from com.codahale.metrics to io.dropwizard.metrics

### Version 1.4.7 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.7%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.7/)) ###

* [Pull 783](https://github.com/Netflix/Hystrix/pull/783) Upgrade to RxJava 1.0.10
* [Pull 781](https://github.com/Netflix/Hystrix/pull/781) Shorten collapser stress test to avoid OOM in Travis
* [Pull 780](https://github.com/Netflix/Hystrix/pull/780) Allow hooks to throw exceptions and not corrupt internal Hystrix state
* [Pull 779](https://github.com/Netflix/Hystrix/pull/779) Use HdrHistogram for capturing latencies
* [Pull 778](https://github.com/Netflix/Hystrix/pull/778) Run jmh using more forks and fewer iterations/fork
* [Pull 776](https://github.com/Netflix/Hystrix/pull/776) Add Bad requests to Hystrix dashboard
* [Pull 775](https://github.com/Netflix/Hystrix/pull/775) Add counters for number of commands, thread pools, groups
* [Pull 774](https://github.com/Netflix/Hystrix/pull/774) Add global concurrent Hystrix threads counter

### Version 1.4.6 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.6%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.6/)) ###

* [Pull 772](https://github.com/Netflix/Hystrix/pull/772) Add try-catch to all hook invocations
* [Pull 773](https://github.com/Netflix/Hystrix/pull/773) Move threadPool field to end of metrics stream
* [Pull 770](https://github.com/Netflix/Hystrix/pull/770) Fix AbstractCommand.isCircuitBreakerOpen() return value when circuit is forced open or closed
* [Pull 769](https://github.com/Netflix/Hystrix/pull/769) Add threadPool to command metrics in event stream
* [Pull 767](https://github.com/Netflix/Hystrix/pull/767) JMH upgrade and multithreaded benchmark

### Version 1.4.5 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.5%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.5/)) ###

* [Pull 764](https://github.com/Netflix/Hystrix/pull/764) Upgrade RxJava from 1.0.7 to 1.0.9
* [Pull 763](https://github.com/Netflix/Hystrix/pull/763) Upgrade Jackson for hystrix-metrics-event-stream from 1.9.2 to 2.5.2
* [Pull 760](https://github.com/Netflix/Hystrix/pull/760) Set Hystrix-created threads to be daemon
* [Pull 757](https://github.com/Netflix/Hystrix/pull/757) Update RxNetty version in hystrix-rx-netty-metrics-stream from 0.3.8 to 0.4.7
* [Pull 755](https://github.com/Netflix/Hystrix/pull/755) Improve Javadoc for HystrixThreadPoolProperties.Setter
* [Pull 754](https://github.com/Netflix/Hystrix/pull/754) Only fire onFallbackStart/onFallbackError hooks when a user-supplied fallback is invoked
* [Pull 753](https://github.com/Netflix/Hystrix/pull/753) Add timeout to dashboard for semaphore commands
* [Pull 750](https://github.com/Netflix/Hystrix/pull/750) First pass at jmh performance benchmarking
* [Pull 748](https://github.com/Netflix/Hystrix/pull/748) Fix return value of HystrixCircuiBreakerImpl.isOpen when it loses a race to open a circuit
* [Pull 746](https://github.com/Netflix/Hystrix/pull/746) Improve Javadoc for HystrixCommandProperties.Setter


### Version 1.4.4 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.4%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.4/)) ###

* [Pull 743](https://github.com/Netflix/Hystrix/pull/743) Proper Javadoc deprecation for command timeouts being thread-specific
* [Pull 742](https://github.com/Netflix/Hystrix/pull/742) Add flag to disable command timeouts
* [Pull 741](https://github.com/Netflix/Hystrix/pull/741) Bugfix to java.lang.Error handling
* [Pull 735](https://github.com/Netflix/Hystrix/pull/735) (Javanica) BatchHystrixCommand
* [Pull 739](https://github.com/Netflix/Hystrix/pull/739) Mark some java.lang.Errors as unrecoverable and never trigger fallback
* [Pull 738](https://github.com/Netflix/Hystrix/pull/738) Filter out thread pools with no thread activity from hystrics-metrics-event-stream
* [Pull 732](https://github.com/Netflix/Hystrix/pull/732) Comment out flaky unit test

### Version 1.4.3 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.3%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.3/)) ###

* [Pull 731](https://github.com/Netflix/Hystrix/pull/731) Revert to Java 6
* [Pull 728](https://github.com/Netflix/Hystrix/pull/728) Add semaphore-rejected count to dashboard
* [Pull 711](https://github.com/Netflix/Hystrix/pull/711) Use Archaius for plugin registration
* [Pull 671](https://github.com/Netflix/Hystrix/pull/671) Stop passing Transfer-Encoding header when streaming metrics

### Version 1.4.2 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.2%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.2/)) ###

* [Pull 723](https://github.com/Netflix/Hystrix/pull/723) Fixed Javanica issue where annotation appeared in superclasses
* [Pull 727](https://github.com/Netflix/Hystrix/pull/727) Fixed TravisCI issue by raising timeout in fallback rejection unit test
* [Pull 724](https://github.com/Netflix/Hystrix/pull/724) Fixed backwards-incompatibility where using a custom HystrixConcurrencyStrategy forced use of a non-null HystrixRequestContext
* [Pull 717](https://github.com/Netflix/Hystrix/pull/717) Added error message to dashboard HTML when connection to metrics source fails

### Version 1.4.1 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.1%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.1/)) ###

* [Pull 716](https://github.com/Netflix/Hystrix/pull/716) Fixed backwards-incompatibility where .execute(), .queue(), .observe(), .toObservable() were all made final in 1.4.0

### Version 1.4.0 ([Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.hystrix%22%20AND%20v%3A%221.4.0%22), [Bintray](https://bintray.com/netflixoss/maven/Hystrix/1.4.0/)) ###

This version adds HystrixObservableCommand and implements both it and HystrixCommand in terms of [Observables](https://github.com/ReactiveX/RxJava).  

A HystrixObservableCommand allows for fully non-blocking commands that can be composed as part of a larger Observable chain.  See [the wiki](https://github.com/Netflix/Hystrix/wiki/How-To-Use#reactive-commands) for more details on usage.  Here's an example (using Java 8):

```java
public class ObservableHttpCommand extends HystrixObsverableCommand<BackendResponse> {

@Override
protected Observable<BackendResponse> construct() {
    return httpClient.submit(HttpClientRequest.createGet("/mock.json?numItems=" + numItems))
        .flatMap((HttpClientResponse<ByteBuf> r) -> r.getContent()
        .map(b -> BackendResponse.fromJson(new ByteBufInputStream(b))));
    }

@Override
protected Observable<BackendResponse> resumeWithFallback() {
    return Observable.just(new BackendResponse(0, numItems, new String[] {}));
}}

```

Because an Observable represents a stream of data, your HystrixObservableCommand may now return a stream of data, and supply a stream of data as a fallback.  The methods to do so are `construct()` and `resumeWithFallback()`, respectively.  All other aspects of the Hystrix state machine work the same in a HystrixCommand.  See [this wiki page](https://github.com/Netflix/Hystrix/wiki/How-it-Works#flow-chart) for a diagram of this state machine.

The public API of HystrixCommand is unchanged, though the internals have significantly changed.  Some bugfixes are now possible that affect Hystrix semantics:

* Timeouts now apply to semaphore-isolated commands as well as thread-isolated commands.  Before 1.4.x, semaphore-isolated commands could not timeout.  They now have a timeout registered on another (HystrixTimer) thread, which triggers the timeout flow.  If you use semaphore-isolated commands, they will now see timeouts.  As all HystrixCommands have a [default timeout](https://github.com/Netflix/Hystrix/wiki/Configuration#execution.isolation.thread.timeoutInMilliseconds), this potentially affects all semaphore-isolated commands.
* Timeouts now fire on `HystrixCommand.queue()`, even if the caller never calls `get()` on the resulting Future.  Before 1.4.x, only calls to `get()` triggered the timeout mechanism to take effect.

You can see more in-depth examples of how the new functionality of Hystrix 1.4 is used at [Netflix/ReactiveLab](https://github.com/Netflix/ReactiveLab).

You can learn more about Observables and RxJava at [Netflix/RxJava](https://github.com/ReactiveX/RxJava).

As this was a major refactoring of Hystrix internals, we (Netflix) have run a release candidate of Hystrix 1.4 in canaries and production over the last month to gain confidence.

* [Pull 701](https://github.com/Netflix/Hystrix/pull/701) Fix Javadoc warnings
* [Pull 700](https://github.com/Netflix/Hystrix/pull/700) Example code for publishing to Graphite

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
