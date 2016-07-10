;
; Copyright 2016 Netflix, Inc.
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;

(ns com.netflix.hystrix.core-test
  (:use com.netflix.hystrix.core)
  (:require [clojure.test :refer [deftest testing is are use-fixtures]])
  (:import [com.netflix.hystrix Hystrix HystrixExecutable]
           [com.netflix.hystrix.strategy.concurrency HystrixRequestContext]
           [com.netflix.hystrix.exception HystrixRuntimeException]))

; reset hystrix after each execution, for consistency and sanity
(defn reset-fixture
  [f]
  (try
    (f)
    (finally
      (Hystrix/reset))))

(use-fixtures :once reset-fixture)

; wrap each test in hystrix context
(defn request-context-fixture
  [f]
  (try
    (HystrixRequestContext/initializeContext)
    (f)
    (finally
      (when-let [c (HystrixRequestContext/getContextForCurrentThread)]
        (.shutdown c)))))

(use-fixtures :each request-context-fixture)

(deftest test-command-key
  (testing "returns nil when input is nil"
    (is (nil? (command-key nil))))

  (testing "returns existing key unchanged"
    (let [k (command-key "foo")]
      (is (identical? k (command-key k)))))

  (testing "Makes a key from a string"
    (let [^com.netflix.hystrix.HystrixCommandKey k (command-key "foo")]
      (is (instance? com.netflix.hystrix.HystrixCommandKey k))
      (is (= "foo" (.name k)))))

  (testing "Makes a key from a keyword"
    (let [^com.netflix.hystrix.HystrixCommandKey k (command-key :bar)]
      (is (instance? com.netflix.hystrix.HystrixCommandKey k))
      (is (= "bar" (.name k))))))

(deftest test-normalize-collapser-scope
  (testing "throws on nil"
    (is (thrown? IllegalArgumentException (collapser-scope nil))))
  (testing "throws on unknown"
    (is (thrown? IllegalArgumentException (collapser-scope :foo))))
  (testing "Returns a scope unchanged"
    (is (= com.netflix.hystrix.HystrixCollapser$Scope/REQUEST
           (collapser-scope com.netflix.hystrix.HystrixCollapser$Scope/REQUEST))))
  (testing "Returns a request scope"
    (is (= com.netflix.hystrix.HystrixCollapser$Scope/REQUEST
           (collapser-scope :request))))
  (testing "Returns a global scope"
    (is (= com.netflix.hystrix.HystrixCollapser$Scope/GLOBAL
           (collapser-scope :global)))))

(deftest test-normalize-command
  (testing "throws if :init-fn isn't a fn"
    (is (thrown-with-msg? IllegalArgumentException #"^.*init-fn.*$"
                          (normalize {:type :command
                                      :run-fn +
                                      :init-fn 999})))))

(deftest test-normalize-collapser
  (testing "throws if :init-fn isn't a fn"
    (is (thrown-with-msg? IllegalArgumentException #"^.*init-fn.*$"
                          (normalize {:type :collapser
                                      :collapse-fn (fn [& args])
                                      :map-fn (fn [& args])
                                      :init-fn "foo"})))))

(deftest test-instantiate
  (let [base-def {:type :command
                  :group-key :my-group
                  :command-key :my-command
                  :run-fn + }]
    (testing "makes a HystrixCommand"
      (let [c (instantiate (normalize base-def))]
        (is (instance? com.netflix.hystrix.HystrixCommand c))))

    (testing "makes a HystrixCommand and calls :init-fn"
      (let [called (atom nil)
            init-fn (fn [d s] (reset! called [d s]) s)
            c (instantiate (normalize (assoc base-def :init-fn init-fn)))
            [d s] @called]
        (is (not (nil? @called)))
        (is (map? d))
        (is (instance? com.netflix.hystrix.HystrixCommand$Setter s))))

    (testing "makes a HystrixCommand that executes :run-fn with given args"
      (let [c (instantiate (normalize base-def) 99 42)]
        (is (= 141 (.execute c)))))

    (testing "makes a HystrixCommand that executes :fallback-fn with given args"
      (let [c (instantiate (normalize (assoc base-def
                                             :run-fn (fn [& args] (throw (IllegalStateException.)))
                                             :fallback-fn -))
                           99 42)]
        (is (= (- 99 42) (.execute c)))))

    (testing "makes a HystrixCommand that implements getCacheKey"
      (with-request-context
        (let [call-count (atom 0) ; make sure run-fn is only called once
               test-def (normalize (assoc base-def
                                     :run-fn       (fn [arg] (swap! call-count inc) (str arg "!"))
                                     :cache-key-fn (fn [arg] arg)))
               result1 (.execute (instantiate test-def "hi"))
               result2 (.execute (instantiate test-def "hi"))]
          (is (= "hi!" result1 result2))
          (is (= 1 @call-count))))))
  (testing "throws if :hystrix metadata isn't found in a var"
    (is (thrown? IllegalArgumentException
                 (instantiate #'map))))
  (testing "throws if it doesn't know what to do"
    (is (thrown? IllegalArgumentException
                 (instantiate [1 2 2])))))

(deftest test-execute
  (let [base-def {:type :command
                  :group-key :my-group
                  :command-key :my-command }]
    (testing "executes a HystrixCommand"
      (is (= "hello-world")
          (execute (instantiate (normalize (assoc base-def :run-fn str))
                                "hello" "-" "world"))))

    (testing "throws HystrixRuntimeException if called twice on same instance"
      (let [instance (instantiate (normalize (assoc base-def :run-fn str)) "hi")]
        (is (thrown? HystrixRuntimeException
                     (execute instance)
                     (execute instance)))))

    (testing "throws if there are trailing args"
      (is (thrown? IllegalArgumentException
                   (execute (instantiate (normalize base-def)) "hello" "-" "world"))))

    (testing "instantiates and executes a command"
      (is (= "hello-world")
          (execute (normalize (assoc base-def :run-fn str))
                   "hello" "-" "world")))))

(deftest test-queue
  (let [base-def {:type :command
                  :group-key :my-group
                  :command-key :my-command
                  :run-fn + }]

    (testing "queues a HystrixCommand"
      (is (= "hello-world")
          (.get (queue (instantiate (normalize (assoc base-def :run-fn str))
                                    "hello" "-" "world")))))

    (testing "throws if there are trailing args"
      (is (thrown? IllegalArgumentException
                   (queue (instantiate (normalize base-def)) "hello" "-" "world"))))

    (testing "instantiates and queues a command"
      (let [qc (queue (normalize (assoc base-def :run-fn str)) "hello" "-" "world")]
        (is (instance? HystrixExecutable (instance qc)))
        (is (future? qc))
        (is (= "hello-world" (.get qc) @qc))
        (is (.isDone qc))))))

(defn ^:private wait-for-observable
  [^rx.Observable o]
  (-> o .toBlocking .single))

(deftest test-observe
  (let [base-def {:type :command
                  :group-key :my-group
                  :command-key :my-command
                  :run-fn + }]
    (testing "observes a HystrixCommand"
      (is (= 99
             (-> (instantiate (normalize base-def) 11 88)
                 observe
                 wait-for-observable))))
    (testing "throws if there are trailing args"
      (is (thrown? IllegalArgumentException
                   (observe (instantiate (normalize base-def)) 10 23))))
    (testing "instantiates and observes a command"
      (let [o (observe (normalize base-def) 75 19 23)]
        (is (instance? rx.Observable o))
        (is (= (+ 75 19 23)
               (wait-for-observable o)))))))

(deftest test-observe-later
  (let [base-def {:type :command
                  :group-key :my-group
                  :command-key :my-command
                  :run-fn + }]
    (testing "observes a HystrixCommand"
      (is (= 99
             (-> (instantiate (normalize base-def) 11 88)
                 observe-later
                 wait-for-observable))))
    (testing "throws if there are trailing args"
      (is (thrown? IllegalArgumentException
                   (observe-later (instantiate (normalize base-def)) 10 23))))
    (testing "instantiates and observes a command"
      (let [o (observe-later (normalize base-def) 75 19 23)]
        (is (instance? rx.Observable o))
        (is (= (+ 75 19 23)
               (wait-for-observable o)))))
    (testing "observes command with a Scheduler"
      (let [o (observe-later-on (normalize base-def)
                                (rx.schedulers.Schedulers/newThread)
                                75 19 23)]
        (is (instance? rx.Observable o))
        (is (= (+ 75 19 23)
               (wait-for-observable o)))))))

(deftest test-this-command-binding
  (let [base-def {:type :command
                  :group-key :test-this-command-binding-group
                  :command-key :test-this-command-binding
                  }]
    (testing "this is bound while :run-fn is executing"
      (let [captured (atom nil)
            command-def (normalize (assoc base-def
                                          :run-fn (fn []
                                                    (reset! captured *command*))))
            command (instantiate command-def)]
        (.execute command)
        (is (identical? command @captured))))


    (testing "this is bound while :fallback-fn is executing"
      (let [captured (atom nil)
            command-def (normalize (assoc base-def
                                          :run-fn (fn [] (throw (Exception. "FALLBACK!")))
                                          :fallback-fn (fn [] (reset! captured *command*))
                                          ))
            command (instantiate command-def)]
        (.execute command)
        (is (identical? command @captured))))))

(deftest test-collapser
  ; These atoms are only for testing. In real life, collapser functions should *never*
  ; have side effects.
  (let [batch-calls (atom [])
        map-fn-calls (atom [])
        collapse-fn-calls (atom [])
        to-upper-batch  (fn [xs] (mapv #(.toUpperCase ^String %) xs))
        ; Define a batch version of to-upper
        command (normalize {:type        :command
                            :group-key   :my-group
                            :command-key :to-upper-batcher
                            :run-fn      (fn [xs]
                                           (swap! batch-calls conj (count xs))
                                           (to-upper-batch xs))})

        ; Define a collapser for to-upper
        collapser (normalize {:type          :collapser
                              :collapser-key :to-upper-collapser
                              :collapse-fn   (fn [arg-lists]
                                               (swap! collapse-fn-calls conj (count arg-lists))
                                               ; batch to-upper takes a list of strings, i.e. the first/only
                                               ; arg of each request
                                               (instantiate command (mapv first arg-lists)))
                              :map-fn        (fn [arg-lists uppered]
                                               (swap! map-fn-calls conj (count arg-lists))
                                               ; batch to-upper returns results in same order as input so
                                               ; we can just to a direct mapping without looking in the
                                               ; results.
                                               uppered) })]
    (testing "that it actually works"
      (let [n 100
            inputs           (mapv #(str "xyx" %) (range n))
            expected-results (to-upper-batch inputs)
            to-upper         (partial queue collapser)
            queued           (doall (map (fn [x]
                                           (Thread/sleep 1)
                                           (to-upper x))
                                         inputs))
            results          (doall (map deref queued))]
        (println "Got collapses with sizes:" @collapse-fn-calls)
        (println "Got maps with sizes:" @map-fn-calls)
        (println "Got batches with sizes:" @batch-calls)
        (is (= n (count expected-results) (count results)))
        (is (= expected-results results))
        (is (not (empty? @collapse-fn-calls)))
        (is (not (empty? @map-fn-calls)))
        (is (not (empty? @batch-calls)))))))

(defcommand my-fn-command
  "A doc string"
  {:meta :data
   :hystrix/fallback-fn (constantly 500)}
  [a b]
  (+ a b))

(defcommand my-overload-fn-command
  "A doc string"
  {:meta :data
   :hystrix/fallback-fn (constantly 500)}
  ([a b]
     (+ a b))
  ([a b c]
     (+ a b c)))

(deftest test-defcommand
  (let [hm (-> #'my-fn-command meta :hystrix)]
    (testing "defines a fn in a var"
      (is (fn? my-fn-command))
      (is (map? hm))
      (is (= "com.netflix.hystrix.core-test/my-fn-command" (.name (:command-key hm))))
      (is (= "com.netflix.hystrix.core-test" (.name (:group-key hm))))
      (is (= :data (-> #'my-fn-command meta :meta)))
      (= 500 ((:fallback-fn hm))))
    (testing "defines a functioning command"
      (is (= 99 (my-fn-command 88 11)))
      (is (= 100 (execute #'my-fn-command 89 11)))
      (is (= 101 (deref (queue #'my-fn-command 89 12))))
      (is (= 103 (wait-for-observable (observe #'my-fn-command 90 13))))
      (is (= 105 (wait-for-observable (observe-later #'my-fn-command 91 14))))
      (is (= 107 (wait-for-observable (observe-later-on #'my-fn-command
                                                        (rx.schedulers.Schedulers/newThread)
                                                        92 15)))))
    (testing "overload functioning command"
      (is (= 99 (my-overload-fn-command 88 11)))
      (is (= 100 (my-overload-fn-command 88 11 1)))
      (is (= 100 (execute #'my-overload-fn-command 89 11)))
      (is (= 100 (execute #'my-overload-fn-command 88 11 1)))
      (is (= 101 (deref (queue #'my-overload-fn-command 89 12))))
      (is (= 102 (deref (queue #'my-overload-fn-command 89 12 1))))
      (is (= 103 (wait-for-observable (observe #'my-overload-fn-command 90 13))))
      (is (= 104 (wait-for-observable (observe #'my-overload-fn-command 90 13 1))))
      (is (= 105 (wait-for-observable (observe-later #'my-overload-fn-command 91 14))))
      (is (= 106 (wait-for-observable (observe-later #'my-overload-fn-command 91 14 1))))
      (is (= 107 (wait-for-observable (observe-later-on #'my-overload-fn-command
                                                        (rx.schedulers.Schedulers/newThread)
                                                        92 15))))
      (is (= 108 (wait-for-observable (observe-later-on #'my-overload-fn-command
                                                        (rx.schedulers.Schedulers/newThread)
                                                        92 15 1)))))))

(defcollapser my-collapser
  "a doc string"
  {:meta :data}
  (collapse [arg-lists] "collapse")
  (map      [batch-result arg-lists] "map"))

(deftest test-defcollapser
  (let [hm (-> #'my-collapser meta :hystrix)]
    (testing "defines a collapser in a var"
      (is (fn? my-collapser))
      (is (map? hm))
      (is (= "com.netflix.hystrix.core-test/my-collapser" (.name (:collapser-key hm))))
      (is (= :collapser (:type hm)))
      (is (= "a doc string" (-> #'my-collapser meta :doc)))
      (is (= :data (-> #'my-collapser meta :meta)))
      (is (= "collapse" ((:collapse-fn hm) nil)))
      (is (= "map" ((:map-fn hm) nil nil))))))

; This is a version of the larger batcher example above using macros
(defcommand to-upper-batcher
  "a batch version of to-upper"
  [xs]
  (mapv #(.toUpperCase ^String %) xs))

(defcollapser to-upper-collapser
  "A collapser that dispatches to to-upper-batcher"
  (collapse [arg-lists]
    (instantiate #'to-upper-batcher (mapv first arg-lists)))
  (map [arg-lists uppered]
    uppered))

(deftest test-defd-collapser
  (testing "that it actually works"
    (let [single-call      (to-upper-collapser "v") ; just to make sure collapser works as a fn
          n 100
          inputs           (mapv #(str "xyx" %) (range n))
          expected-results (to-upper-batcher inputs)
          to-upper         (partial queue #'to-upper-collapser)
          queued           (doall (map (fn [x]
                                         (Thread/sleep 1)
                                         (to-upper x))
                                       inputs))
          results          (doall (map deref queued))]
      (is (= "V" single-call))
      (is (= n (count expected-results) (count results)))
      (is (= expected-results results)))))
