;;
;; Copyright 2015 Netflix, Inc.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns com.netflix.hystrix.core
  "THESE BINDINGS ARE EXPERIMENTAL AND SUBJECT TO CHANGE

  Functions for defining and executing Hystrix dependency commands and collapsers.

  The definition of commands and collapers is separated from their instantiation and execution.
  They are represented as plain Clojure maps (see below) which are later instantiated into
  functional HystrixCommand or HystrixCollapser instances. defcommand and defcollapser macros
  are provided to assist in defining these maps.

  A command or collapser definition map can be passed to the execute and queue functions to
  invoke the command.

  The main Hystrix documentation can be found at https://github.com/Netflix/Hystrix. In particular,
  you may find the Javadocs useful if you need to drop down into Java:
  http://netflix.github.io/Hystrix/javadoc/

  # HystrixCommand

  A HystrixCommand is a representation for an interaction with a network dependency, or other
  untrusted resource. See the Hystrix documentation (https://github.com/Netflix/Hystrix) for
  more details.

  A command is defined by a map with the following keys:

    :type

      Always :command. Required.

    :group-key

      A HystrixCommandGroupKey, string, or keyword. Required.

    :command-key

      A HystrixCommandKey, string, or keyword. Required.

    :thread-pool-key

      A HystrixThreadPoolKey, string, or keyword. Optional, defaults to :group-key.

    :run-fn

      The function to run for the command. The function may have any number of
      arguments. Required.

    :fallback-fn

      A function with the same args as :run-fn that calculates a fallback result when
      the command fails. Optional, defaults to a function that throws UnsupportedOperationException.

    :cache-key-fn

      A function which the same args as :run-fn that calculates a cache key for the
      given args. Optional, defaults to nil, i.e. no caching.

    :init-fn

      A function that takes a definition map and HystrixCommand$Setter which should return
      a HystrixCommand$Setter (usually the one passed in) to ultimately be passed to the
      constructor of the HystrixCommand. For example,

          (fn [_ setter]
            (.andCommandPropertiesDefaults setter ...))

      This is your escape hatch into raw Hystrix.
      ... but NOTE: Hystrix does a fair bit of configuration caching and that caching is keyed
      by command key. Thus, many of the settings you apply within :init-fn will only apply
      the *first time it is invoked*. After that, they're ignored. This means that some REPL-based
      dynamicism is lost and that :init-fn shouldn't be used to configure a HystrixCommand at
      run-time. Instead use Archaius properties as described in the Hystrix docs.

  The com.netflix.hystrix.core/defcommand macro is a helper for defining this map and storing it
  in a var. For example, here's a definition for an addition command:

    ; Define a command with :group-key set to the current namespace, *ns*, and with :command-key
    ; set to \"plus\". The function the command executes is clojure.core/+.
    (defcommand plus
      \"My resilient addition operation\"
      [& args]
      (apply + args))
    ;=> #'user/plus

    ; Execute the command
    (plus 1 2 3 4 5)
    ;=> 15

    ; Queue the command for async operation
    (def f (queue #'plus 4 5))
    ;=> java.util.concurrent.Future/clojure.lang.IDeref

    ; Now you can deref the future as usual
    @f   ; or (.get f)
    ;=> 9

  # HystrixCollapser

  A HystrixCollapser allows multiple HystrixCommand requests to be batched together if the underlying
  resource provides such a capability. See the Hystrix documentation (https://github.com/Netflix/Hystrix)
  for more details.

  A collapser is defined by a map with the following keys:

    :type

      Always :collapser. Required.

    :collapser-key

      A HystrixCollapserKey, string, or keyword. Required.

    :collapse-fn

      A fn that takes a sequence of arg lists and instantiates a new command to
      execute them. Required. See com.netflix.hystrix.core/instantiate.  This
      function should be completely free of side effects.

    :map-fn

      A fn that takes sequence of arg lists (as passed to :collapse-fn) and the
      result from the command created by :collapse-fn. Must return a sequence of
      results where the nth element is the result or exception associated with the
      nth arg list. The arg lists are in the same order as passed to :collapse-fn.
      Required.  This function should be completely free of side effects.

    :scope

      The collapser scope, :request or :global. Optional, defaults to :request.

    :shard-fn

      A fn that takes a sequence of arg lists and shards them, returns a sequence of
      sequence of arg lists. Optional, defaults to no sharding.  This function should
      be completely free of side effects.

    :cache-key-fn

      A function that calculates a String cache key for the args passed to the
      collapser. Optional, defaults to a function returning nil, i.e. no caching.
      This function should be completely free of side effects.

    :init-fn

      A function that takes a definition map and HystrixCollapser$Setter which should return
      a HystrixCollapser$Setter (usually the one passed in) to ultimately be passed to the
      constructor of the HystrixCollapser. For example,

          (fn [_ setter]
            (.andCollapserPropertiesDefaults setter ...))

      This is your escape hatch into raw Hystrix. Please see additional notes about :init-fn
      above. They apply to collapsers as well.

  The com.netflix.hystric.core/defcollapser macro is a helper for defining this map and storing it
  in a callable var.
  "
  (:require [clojure.set :as set])
  (:import [java.util.concurrent Future]
           [com.netflix.hystrix
              HystrixExecutable
              HystrixCommand
              HystrixCommand$Setter
              HystrixCollapser
              HystrixCollapser$Scope
              HystrixCollapser$Setter
              HystrixCollapser$CollapsedRequest]
           [com.netflix.hystrix.strategy.concurrency
             HystrixRequestContext]))

(set! *warn-on-reflection* true)

(defmacro ^:private key-fn
  "Make a function that creates keys of the given class given one of:

      * an instance of class
      * a string name
      * a keyword name
  "
  [class]
  (let [s (-> class name (str "$Factory/asKey") symbol)]
    `(fn [key-name#]
       (cond
         (nil? key-name#)
          nil
         (instance? ~class key-name#)
           key-name#
         (keyword? key-name#)
           (~s (name key-name#))
         (string? key-name#)
           (~s key-name#)
         :else
           (throw (IllegalArgumentException. (str "Don't know how to make " ~class " from " key-name#)))))))

(def command-key
  "Given a string or keyword, returns a HystrixCommandKey. nil and HystrixCommandKey instances
  are returned unchanged.

  This function is rarely needed since most hystrix-clj functions will do this automatically.

  See:
    com.netflix.hystrix.HystrixCommandKey
  "
  (key-fn com.netflix.hystrix.HystrixCommandKey))

(def group-key
  "Given a string or keyword, returns a HystrixCommandGroupKey. nil and HystrixCommandGroupKey
  instances are returned unchanged.

  This function is rarely needed since most hystrix-clj functions will do this automatically.

  See:
    com.netflix.hystrix.HystrixCommandGroupKey
  "
  (key-fn com.netflix.hystrix.HystrixCommandGroupKey))

(def thread-pool-key
  "Given a string or keyword, returns a HystrixThreadPoolGroupKey. nil and HystrixThreadPoolKey
  instances are returned unchanged.

  This function is rarely needed since most hystrix-clj functions will do this automatically.

  See:
    com.netflix.hystrix.HystrixThreadPoolKey
  "
  (key-fn com.netflix.hystrix.HystrixThreadPoolKey))

(def collapser-key
  "Given a string or keyword, returns a HystrixCollapserKey. nil and HystrixCollapserKey
  instances are returned unchanged.

  This function is rarely needed since most hystrix-clj functions will do this automatically.

  See:
    com.netflix.hystrix.HystrixCollapserKey
  "
  (key-fn com.netflix.hystrix.HystrixCollapserKey))

(defn ^HystrixCollapser$Scope collapser-scope
  [v]
  (cond
    (instance? HystrixCollapser$Scope v)
      v
    (= :request v)
      HystrixCollapser$Scope/REQUEST
    (= :global v)
      HystrixCollapser$Scope/GLOBAL
    :else
      (throw (IllegalArgumentException. (str "Don't know how to make collapser scope from '" v "'")))))

(defn- required-key
  [k p msg]
  (fn [d]
    (when-not (contains? d k)
      (throw (IllegalArgumentException. (str k " is required."))))
    (when-not (p (get d k))
      (throw (IllegalArgumentException. (str k " " msg "."))))
    d))

(defn- optional-key
  [k p msg]
  (fn [d]
    (if-let [v (get d k)]
      (when-not (p v)
        (throw (IllegalArgumentException. (str k " " msg ".")))))
    d))
(defn- required-fn [k] (required-key k ifn? "must be a function"))
(defn- optional-fn [k] (optional-key k ifn? "must be a function"))

;################################################################################

(defmulti normalize
  "Given a definition map, verify and normalize it, expanding shortcuts to fully-qualified objects, etc.

  Throws IllegalArgumentException if any constraints for the definition map are violated.
  "
  (fn [definition] (:type definition)))

(defmulti instantiate* (fn [definition & _] (:type definition)))

;################################################################################

(def ^{:dynamic true :tag HystrixCommand} *command*
  "A dynamic var which is bound to the HystrixCommand instance during execution of
  :run-fn and :fallback-fn.

  It's occasionally useful, especially for fallbacks, to base the result on the state of
  the comand. The fallback might vary based on whether it was triggered by an application
  error versus a timeout.

  Note: As always with dynamic vars be careful about scoping. This binding only holds for
  the duration of the :run-fn or :fallback-fn.
  "
  nil)

;################################################################################

(defmacro with-request-context
  "Executes body within a new Hystrix Context.

  Initializes a new HystrixRequestContext, executes the code and then shuts down the
  context. Evaluates to the result of body.

  Example:

    (with-request-context
      (... some code that uses Hystrix ...))

  See:
    com.netflix.hystrix.strategy.concurrency.HystrixRequestContext
  "
  [& body]
  `(let [context# (HystrixRequestContext/initializeContext)]
     (try
       ~@body
       (finally
         (.shutdown context#)))))

(defn command
  "Helper function that takes a definition map for a HystrixCommand and returns a normalized
  version ready for use with execute and queue.

  See com.netflix.hystrix.core ns documentation for valid keys.

  See:
    com.netflix.hystrix.core/defcommand
  "
  [options-map]
  (-> options-map
    (assoc :type :command)
    normalize))

(defn- split-def-meta
  "split meta map out of def-style args list"
  [opts]
  (let [doc?      (string? (first opts))
        m         (if doc? {:doc (first opts)} {})
        opts      (if doc? (rest opts) opts)

        attrs?    (map? (first opts))
        m         (if attrs? (merge (first opts) m) m)
        opts      (if attrs? (rest opts) opts)]
    [m opts]))

(defn- extract-hystrix-command-options
  [meta-map]
  (let [key-map {:hystrix/cache-key-fn    :cache-key-fn
                 :hystrix/fallback-fn     :fallback-fn
                 :hystrix/group-key       :group-key
                 :hystrix/command-key     :command-key
                 :hystrix/thread-pool-key :thread-pool-key
                 :hystrix/init-fn         :init-fn }]
    (set/rename-keys (select-keys meta-map (keys key-map)) key-map)))

(defmacro defcommand
  "A macro with the same basic form as clojure.core/defn exception that it wraps the body
  of the function in a HystrixCommand. This allows an existing defn to be turned into
  a command by simply change \"defn\" to \"defcommand\".

  Additional command options can be provided in the defn attr-map, qualifying their keys with
  the :hystrix namespace, e.g. :thread-pool-key becomes :hystrix/thread-pool-key. Obviously,
  :hystrix/run-fn is ignored since it's inferred from the body of the macro.

  The *var* defined by this macro can be passed to the execute and queue functions as if
  it were a HystrixCommand definition map. The complete definition map is stored under the
  :hystrix key in the var's metadata.

  See com.netflix.hystrix.core ns documentation for valid keys.

  Example:

    (defcommand search
      \"Fault tolerant search\"
      [term]
      ... execute service request and return vector of results ...)

    ; Same as above, but add fallback and caching
    (defcommand search
      \"Fault tolerant search\"
      {:hystrix/cache-key-fn identity
       :hystrix/fallback-fn  (constantly []))}
      [term]
      ... execute service request and return vector of results ...)

    ; Call it like a normal function
    (search \"The Big Lebowski\")
    ;=>  [... vector of results ...]

    ; Asynchronously execute the search command
    (queue #'search \"Fargo\")
    ;=> a deref-able future
  "
  {:arglists '([name doc-string? attr-map? [params*] & body])}
  [name & opts]
  (let [command-key         (str *ns* "/" name )
        group-key           (str *ns*)
        [m body]            (split-def-meta opts)
        params              (if (vector? (first body))
                              (list (first body))
                              (map first body))
        m                   (if-not (contains? m :arglists)
                              (assoc m :arglists ('quote `(~params)))
                              m)]
    `(let [meta-options# (#'com.netflix.hystrix.core/extract-hystrix-command-options ~m)
           run-fn#       (fn ~name ~@body)
           command-map#  (com.netflix.hystrix.core/command (merge {:command-key   ~command-key
                                                                   :group-key     ~group-key
                                                                   :run-fn        run-fn# }
                                                                  meta-options#))]
       (def ~(with-meta name m)
         (fn [& args#]
           (apply com.netflix.hystrix.core/execute command-map# args#)))
       (alter-meta! (var ~name) assoc :hystrix command-map#)
       (var ~name))))

(defn- extract-hystrix-collapser-options
  [meta-map]
  (let [key-map {:hystrix/collapser-key :collapser-key
                 :hystrix/shard-fn      :shard-fn
                 :hystrix/scope         :scope
                 :hystrix/cache-key-fn  :cache-key-fn
                 :hystrix/init-fn       :init-fn }]
    (set/rename-keys (select-keys meta-map (keys key-map)) key-map)))

(defn collapser
  "Helper function that takes a definition map for a HystrixCollapser and returns a normalized
  version ready for use with execute and queue.

  See com.netflix.hystrix.core ns documentation for valid keys.

  See:
    com.netflix.hystrix.core/defcollapser
  "
  [{:keys [collapser-key] :as options-map}]
  (let [result (-> options-map
                 (assoc :type :collapser)
                 normalize)]
    result))

(defmacro defcollapser
  "Define a new collapser bound to the given var with a collapser key created from the current
  namespace and the var name. Like clojure.core/defn, takes an optional doc string and metadata
  map. The form is similar to defn except that a body for both :map-fn and :collapse-fn must be
  provided:

    (defcollapser my-collapser
      \"optional doc string\"
      {... optional attr map with var metadata ...}
      (collapse [arg-lists] ... body of :collapse-fn ...)
      (map      [arg-lists batch-result] ... body of :map-fn ...))

  Additional collapser options can be provided in the attr-map, qualifying their keys with
  the :hystrix namespace, e.g. :scope becomes :hystrix/scope. Obviously,
  :hystrix/collapse-fn and :hystrix/map-fn are ignored since they're inferred from the body
  of the macro.

  See com.netflix.hystrix.core ns documentation for valid keys.

  Example:

     (ns my-namespace
       :require  com.netflix.hystrix.core :refer [defcommand defcollapser instantiate execute queue])

    ; Suppose there's an existing multi-search command that takes a sequence of multiple search
    ; terms and returns a vector of vector of search results with a single server request.
    (defcommand multi-search ...)

    ; Now we can define single-term search as a collapser that will collapse multiple
    ; in-flight search requests into a single multi-term search request to the server
    (defcollapser search
      \"Collapsing single-term search command\"
      (collapse [arg-lists]
        ; Create a multi-search command, passing individual terms as a seq of args
        (instantiate multi-search (map first arg-lists)))
      (map [arg-lists batch-result]
        ; Map from input args to results. Here we assume order is preserve by
        ; multi-search so we can return the result list directly
        batch-result))

    ; The search collapser is now defined. It has a collapser key of \"my-namespace/search\".
    ; This is used for configuration and metrics.

    ; Syncrhonously execute the search collapser
    (search \"The Hudsucker Proxy\")
    ;=> [... vector of results ...]

    ; Asynchronously execute the search collapser
    (queue search \"Raising Arizona\")
    ;=> a deref-able future
  "
  {:arglists '([name doc-string? attr-map?
                (collapse [arg-lists] :collapse-fn body)
                (map      [arg-lists batch-result] :map-fn body)])}
  [name & opts]
  (let [full-name (str *ns* "/" name)
        [m fns]   (split-def-meta opts)
        _         (when-not (= 2 (count fns))
                    (throw (IllegalArgumentException. "Expected collapse and map forms.")))
        getfn     (fn [s] (first (filter #(= s (first %)) fns)))
        [_ collapse-args & collapse-body] (getfn 'collapse)
        [_ map-args      & map-body]      (getfn 'map)]
    `(let [meta-options# (#'com.netflix.hystrix.core/extract-hystrix-collapser-options ~m)
           map-fn#       (fn ~map-args ~@map-body)
           collapse-fn#  (fn ~collapse-args ~@collapse-body)
           def-map# (com.netflix.hystrix.core/collapser (merge {:collapser-key ~full-name
                                                                :map-fn map-fn#
                                                                :collapse-fn collapse-fn# }
                                                               meta-options#))]
       (def ~(with-meta name m)
         (fn [& args#]
           (apply com.netflix.hystrix.core/execute def-map# args#)))
       (alter-meta! (var ~name) assoc :hystrix def-map#)
       (var ~name))))

(defn ^HystrixExecutable instantiate
  "Given a normalized definition map for a command or collapser, 'compiles' it into a HystrixExecutable object
  that can be executed, queued, etc. This function should rarely be used. Use execute and queue
  instead.

  definition may be any of:

    * A var created with defcommand
    * A var created with defcollapser
    * A normalized definition map for a command
    * A HystrixExecutable instance and no additional arguments

  One typical use case for this function is to create a batch command in the :collapse-fn of a collapser. Another is to get an actual HystrixCommand instance to get access to additional methods
  it provides.

  See:
    com.neflix.hystrix.core/normalize
    com.neflix.hystrix.core/execute
    com.neflix.hystrix.core/queue
  "
  {:arglists '[[defcommand-var & args]
               [defcollapser-var & args]
               [definition-map & args]
               [HystrixExecutable] ]}
  [definition & args]
  (cond
    (var? definition)
      (if-let [hm (-> definition meta :hystrix)]
        (apply instantiate* hm  args)
        (throw (IllegalArgumentException. (str "Couldn't find :hystrix metadata on var " definition))))

    (map? definition)
      (apply instantiate* definition args)

    (instance? HystrixExecutable definition)
      (if (empty? args)
        definition
        (throw (IllegalArgumentException. "Trailing args when executing raw HystrixExecutable")))

    :else
      (throw (IllegalArgumentException. (str "Don't know how to make instantiate HystrixExecutable from: " definition)))))

(defn execute
  "Synchronously execute the command or collapser specified by the given normalized definition with
  the given arguments. Returns the result of :run-fn.

  NEVER EXECUTE A HystrixExecutable MORE THAN ONCE.

  See:
    http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/HystrixCommand.html#execute()
  "
  [definition & args]
  (.execute ^HystrixExecutable (apply instantiate definition args)))

(defprotocol QueuedCommand
  "Protocol implemented by the result of com.netflix.hystrix.core/queue"
  (instance [this] "Returns the raw HystrixExecutable instance created by the queued command"))

(defn- queued-command [^HystrixExecutable instance ^Future future]
  (reify
    QueuedCommand
      (instance [this] instance)

    Future
      (get [this] (.get future))
      (get [this timeout timeunit] (.get future timeout timeunit))
      (isCancelled [this] (.isCancelled future))
      (isDone [this] (.isDone future))
      (cancel [this may-interrupt?] (.cancel future may-interrupt?))

    clojure.lang.IDeref
      (deref [this] (.get future))))

(defn queue
  "Asynchronously queue the command or collapser specified by the given normalized definition with
  the given arguments. Returns an object which implements both java.util.concurrent.Future and
  clojure.lang.IDeref.

  The returned object also implements the QueuedCommand protocol.

  If definition is already a HystrixExecutable and no args are given, queues it and returns
  the same object as described above. NEVER QUEUE A HystrixExecutable MORE THAN ONCE.

  Examples:

    (let [qc (queue my-command 1 2 3)]
      ... do something else ...
      ; wait for result
      @qc)

  See:
    http://netflix.github.com/Hystrix/javadoc/com/netflix/hystrix/HystrixCommand.html#queue()
  "
  [definition & args]
  (let [^HystrixExecutable instance (apply instantiate definition args)]
    (queued-command instance (.queue instance))))

(defn observe
  "Asynchronously execute the command or collapser specified by the given normalized definition
  with the given arguments. Returns an rx.Observable which can be subscribed to.

  Note that this will eagerly begin execution of the command, even if there are no subscribers.
  Use observe-later for lazy semantics.

  If definition is already a HystrixExecutable and no args are given, observes it and returns
  an Observable as described above. NEVER OBSERVE A HystrixExecutable MORE THAN ONCE.

  See:
    http://netflix.github.io/Hystrix/javadoc/com/netflix/hystrix/HystrixCommand.html#observe()
    http://netflix.github.io/Hystrix/javadoc/com/netflix/hystrix/HystrixCollapser.html#observe()
    http://netflix.github.io/RxJava/javadoc/rx/Observable.html
  "
  [definition & args]
  (let [^HystrixExecutable instance (apply instantiate definition args)]
    (.observe instance)))

(defprotocol ^:private ObserveLater
  "A protocol solely to eliminate reflection warnings because .toObservable
  can be found on both HystrixCommand and HystrixCollapser, but not in their
  common base class HystrixExecutable."
  (^:private observe-later* [this])
  (^:private observe-later-on* [this scheduler]))

(extend-protocol ObserveLater
  HystrixCommand
    (observe-later* [this] (.toObservable this))
    (observe-later-on* [this scheduler] (.observeOn (.toObservable this) scheduler))
  HystrixCollapser
    (observe-later* [this] (.toObservable this))
    (observe-later-on* [this scheduler] (.observeOn (.toObservable this) scheduler)))

(defn observe-later
  "Same as #'com.netflix.hystrix.core/observe, but command execution does not begin until the
  returned Observable is subscribed to.

  See:
    http://netflix.github.io/Hystrix/javadoc/com/netflix/hystrix/HystrixCommand.html#toObservable())
    http://netflix.github.io/RxJava/javadoc/rx/Observable.html
  "
  [definition & args]
  (observe-later* (apply instantiate definition args)))

(defn observe-later-on
  "Same as #'com.netflix.hystrix.core/observe-later but an explicit scheduler can be provided
  for the callback.

  See:
    com.netflix.hystrix.core/observe-later
    com.netflix.hystrix.core/observe
    http://netflix.github.io/Hystrix/javadoc/com/netflix/hystrix/HystrixCommand.html#toObservable(Scheduler)
    http://netflix.github.io/RxJava/javadoc/rx/Observable.html
  "
  [definition scheduler & args]
  (observe-later-on* (apply instantiate definition args) scheduler))

;################################################################################
; :command impl

(defmethod normalize :command
  [definition]
  (-> definition
    ((required-fn :run-fn))
    ((optional-fn :fallback-fn))
    ((optional-fn :cache-key-fn))
    ((optional-fn :init-fn))

    (update-in [:group-key] group-key)
    (update-in [:command-key] command-key)
    (update-in [:thread-pool-key] thread-pool-key)))

(defmethod instantiate* :command
  [{:keys [group-key command-key thread-pool-key
           run-fn fallback-fn cache-key-fn
           init-fn] :as def-map} & args]
  (let [setter (-> (HystrixCommand$Setter/withGroupKey group-key)
                 (.andCommandKey    command-key)
                 (.andThreadPoolKey thread-pool-key))
        setter (if init-fn
                 (init-fn def-map setter)
                 setter)]
    (when (not (instance? HystrixCommand$Setter setter))
      (throw (IllegalStateException. (str ":init-fn didn't return HystrixCommand$Setter instance"))))
    (proxy [HystrixCommand] [^HystrixCommand$Setter setter]
      (run []
        (binding [*command* this]
          (apply run-fn args)))
      (getFallback []
        (if fallback-fn
          (binding [*command* this]
            (apply fallback-fn args))
          (throw (UnsupportedOperationException. "No :fallback-fn provided"))))
      (getCacheKey [] (if cache-key-fn
                        (apply cache-key-fn args))))))


;################################################################################
; :collapser impl

(defmethod normalize :collapser
  [definition]
  (-> definition
    ((required-fn :collapse-fn))
    ((required-fn :map-fn))
    ((optional-fn :shard-fn))
    ((optional-fn :cache-key-fn))
    ((optional-fn :init-fn))

    (update-in [:collapser-key] collapser-key)
    (update-in [:scope]         (fnil collapser-scope HystrixCollapser$Scope/REQUEST))))

(defn- collapsed-request->arg-list
  [^HystrixCollapser$CollapsedRequest request]
  (.getArgument request))

(defmethod instantiate* :collapser
  [{:keys [collapser-key scope
           collapse-fn map-fn shard-fn cache-key-fn
           init-fn] :as def-map} & args]
  (let [setter (-> (HystrixCollapser$Setter/withCollapserKey collapser-key)
                 (.andScope scope))
        setter (if init-fn
                 (init-fn def-map setter)
                 setter)]
    (when (not (instance? HystrixCollapser$Setter setter))
      (throw (IllegalStateException. (str ":init-fn didn't return HystrixCollapser$Setter instance"))))
    (proxy [HystrixCollapser] [^HystrixCollapser$Setter setter]
      (getCacheKey [] (if cache-key-fn
                        (apply cache-key-fn args)))

      (getRequestArgument [] args)

      (createCommand [requests]
        (collapse-fn (map collapsed-request->arg-list requests)))

      (shardRequests [requests]
        (if shard-fn
          [requests] ; TODO implement sharding
          [requests]))

      (mapResponseToRequests [batch-response requests]
        (let [arg-lists        (map collapsed-request->arg-list requests)
              mapped-responses (map-fn arg-lists batch-response)]
          (if-not (= (count requests) (count mapped-responses))
            (throw (IllegalStateException.
                     (str ":map-fn of collapser '" collapser-key
                          "' did not return a result for each request. Expected " (count requests)
                          ", got " (count mapped-responses)))))
          (doseq [[^HystrixCollapser$CollapsedRequest request response] (map vector requests mapped-responses)]
            (if (instance? Exception response)
              (.setException request ^Exception response)
              (.setResponse request response))))))))
