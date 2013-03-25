# Hystrix Network Auditor Agent

Original Issue: https://github.com/Netflix/Hystrix/issues/116

This is a [Java Agent](http://docs.oracle.com/javase/6/docs/api/java/lang/instrument/package-summary.html) that instruments network access to allow auditing whether it is being performed within the context of Hystrix or not.

The intent is to enable building a listener to:

- find unknown vulnerabilities (network traffic that was unknown)
- track "drift" over time as transitive dependencies pull in code that performs network access
- track metrics and optionally provide alerts
- allow failing a canary build if unexpected network access occurs
- alert in production if unexpected network access starts (such as if a property is flipped to turn on a feature)


# Why?

This module originates out of work to deal with the "unknowns" that continue to cause failure after wrapping all known network access in Hystrix.

The Netflix API team successfully eliminated a large percentage of the class of errors and outages caused by network latency to backend services but maintaining this state of protection is difficult with constantly changing code and 3rd party libraries.

In other words it's easy to either be unaware of a vulnerability or "[drift into failure](http://www.amazon.com/Drift-into-Failure-ebook/dp/B009KOKXKY/ref=tmm_kin_title_0)" as the state of the system changes over time.


# How to Use

1) Enable the Java Agent

Add the following to the JVM command-line:

```
-javaagent:/var/root/hystrix-network-auditor-agent-x.y.zjar
```

This will be loaded in the boot classloader and instrument `java.net` and `java.io` classes.

2) Register an Event Listener

In the application register a listener to be invoked on each network event:

```java
com.netflix.hystrix.contrib.networkauditor.HystrixNetworkAuditorAgent.registerEventListener(eventListener)
```

3) Handle Events

It is up to the application to decide what to do but generally it is expected that an implementation will filter to only calls not wrapped in Hystrix and then determine the stack trace so the code path can be identified.

Here is an example that increments an overall counter and records the stack trace with a counter per unique stack trace:

```java
    @Override
    public void handleNetworkEvent() {
        if (Hystrix.getCurrentThreadExecutingCommand() == null) {
            // increment counter
            totalNonIsolatedEventsCounter.increment();
            // capture the stacktrace and record so network events can be debugged and tracked down
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            HystrixNetworkEvent counter = counters.get(stack);
            if (counter == null) {
                counter = new HystrixNetworkEvent(stack);
                HystrixNetworkEvent c = counters.putIfAbsent(Arrays.toString(stack), counter);
                if (c != null) {
                    // another thread beat us
                    counter = c;
                }
            }
            counter.increment();
        }
    }
```

# Deployment Model

This is not expected to run on all production instances but as part of a canary process.

For example the Netflix API team intends to have long-running canaries using this agent and treat it like "canaries in the coalmine" that are always running and alert us if network traffic shows up that we are not aware of and not wrapped by Hystrix.

More information about experience will come over time ... this is open-source so we're developing in public!
