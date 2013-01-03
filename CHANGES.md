# Hystrix Releases #

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
