## hystrix-contrib

This is the parent of all "contrib" submodules to Hystrix. 

Examples of what makes sense as a contrib submodule are:

- alternate implementations of [HystrixMetricsPublisher](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/strategy/metrics/HystrixMetricsPublisher.html)
- alternate implementations of [HystrixPropertiesStrategy](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/strategy/properties/HystrixPropertiesStrategy.html)
- request lifecycle implementations (such as [hystrix-request-servlet](https://github.com/Netflix/Hystrix/tree/master/hystrix-contrib/hystrix-request-servlet))
- implementations of [HystrixEventNotifier](http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/strategy/eventnotifier/HystrixEventNotifier.html)
- dashboard and monitoring tools

3rd partly libraries wrapped with Hystrix do not belong here and should be their own project.

They can however be referenced from the Wiki [Libraries](https://github.com/Netflix/Hystrix/wiki/Libraries) page.
