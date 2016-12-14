# hystrix-examples-webapp

Web application that demonstrates functionality from [hystrix-examples](https://github.com/Netflix/Hystrix/tree/master/hystrix-examples) and functionality from [hystrix-request-servlet](https://github.com/Netflix/Hystrix/tree/master/hystrix-contrib/hystrix-request-servlet) and [hystrix-metrics-event-stream](https://github.com/Netflix/Hystrix/tree/master/hystrix-contrib/hystrix-metrics-event-stream).

The [hystrix-dashboard](https://github.com/Netflix/Hystrix/tree/master/hystrix-dashboard) can be used on this example app to monitor its metrics.

# Run via Gradle

```
$ git clone git@github.com:Netflix/Hystrix.git
$ cd Hystrix/hystrix-examples-webapp
$ ../gradlew appRun
> Building > :hystrix-examples-webapp:appRun > Running at http://localhost:8989/hystrix-examples-webapp
```

Once running, open <a href="http://localhost:8989/hystrix-examples-webapp">http://localhost:8989/hystrix-examples-webapp</a>.


<img src="https://raw.github.com/wiki/Netflix/Hystrix/images/hystrix-examples-webapp-home.png">
