# Hystrix Clojure Bindings

This module contains idiomatic Clojure bindings for Hystrix.

# Binaries

Binaries and dependency information for Maven, Ivy, Gradle and others can be found at [http://search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22hystrix-clj%22).

Example for Maven:

```xml
<dependency>
    <groupId>com.netflix.hystrix</groupId>
    <artifactId>hystrix-clj</artifactId>
    <version>x.y.z</version>
</dependency>
```

and for Ivy:

```xml
<dependency org="com.netflix.hystrix" name="hystrix-clj" rev="x.y.z" />
```

and for Leiningen:

```clojure
[com.netflix.hystrix/hystrix-clj "x.y.z"]
```

# Usage

Please see the docstrings in src/com/netflix/hystrix/core.clj for extensive usage info.

## TL;DR
You have a function that interacts with an untrusted dependency:

```clojure
(defn make-request
  [arg]
  ... make the request ...)

; execute the request
(make-request "baz")
```

and you want to make it a Hystrix dependency command. Do this:

```clojure
(defcommand make-request
  [arg]
  ... make the request ...)

; execute the request
(make-request "baz")

; or queue for async execution
(queue #'make-request "baz")
```

# Event Stream

A Clojure version of hystrix-event-stream can be found at https://github.com/josephwilk/hystrix-event-stream-clj
