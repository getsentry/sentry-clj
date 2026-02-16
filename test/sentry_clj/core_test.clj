(ns sentry-clj.core-test
  (:require
   [cheshire.core :as json]
   [expectations.clojure.test :refer [defexpect expect expecting]]
   [sentry-clj.core :as sut])
  (:import
   [io.sentry Breadcrumb EventProcessor Instrumenter JsonSerializer SentryLevel SentryOptions]
   [io.sentry.protocol Request User]
   [java.io StringWriter]
   [java.util Date HashMap UUID]))

(defexpect keyword->level-test
  (expecting "keyword maps to level correctly"
    (expect SentryLevel/DEBUG (#'sut/keyword->level :debug))
    (expect SentryLevel/INFO (#'sut/keyword->level :info))
    (expect SentryLevel/WARNING (#'sut/keyword->level :warning))
    (expect SentryLevel/ERROR (#'sut/keyword->level :error))
    (expect SentryLevel/FATAL (#'sut/keyword->level :fatal))))

(defexpect java-util-hashmappify-vals-tests
  (expecting "everything is a string"
    (expect {"a" "b"} (#'sut/java-util-hashmappify-vals {:a :b}))
    (expecting "nested maps are visited and turned into hashmaps"
      (let [result (#'sut/java-util-hashmappify-vals {:a {:b {:c :d}}})]
        (expect {"a" {"b" {"c" "d"}}} result)
        (expect HashMap (.getClass ^HashMap result))
        (expect HashMap (.getClass ^HashMap (get result "a")))
        (expect HashMap (.getClass ^HashMap (get-in result ["a" "b"])))))
    (expect {"var1" "val1" "var2" {"a" {"b" {"c" {["d" 1] {"e" ["f"]} "g" "h"}}}}} (#'sut/java-util-hashmappify-vals {:var1 "val1" :var2 {:a {:b {:c {[:d 1] {:e [:f]} :g :h}}}}})))
  (expecting "keyword namespaces are preserved"
    (expect {"foo/qux" "some/value" "bar/qux" "another/value"} (#'sut/java-util-hashmappify-vals {:foo/qux :some/value :bar/qux :another/value})))
  (expecting "nested arrays containing keywords are stringified"
    (expect ["foo/qux" ["some/value" "bar/qux"] [["another/value"]]] (#'sut/java-util-hashmappify-vals [:foo/qux [:some/value :bar/qux] [[:another/value]]]))))

(def event
  {:event-id (UUID/fromString "4c4fbea9-57a7-4c99-808d-2284306e6c98")
   :message {:message "ok" :params ["foo" "bar"]}
   :level :info
   :dist "arch"
   :release "v1.0.0"
   :environment "production"
   :user {:email "foo@bar.com"
          :id "id"
          :username "username"
          :ip-address "10.0.0.0"
          :data {"a" "b"}}
   :request {:url "http://example.com"
             :method "GET"
             :query-string "?foo=bar"
             :cookies "cookie1=foo;cookie2=bar"
             :headers {"Cookie" "cookie1=foo;cookie2=bar"
                       "X-Clacks-Overhead" "Terry Pratchett"
                       "X-w00t" "ftw!"}
             :env {"a" "b"}
             :data {"c" "d"}
             :other {"x" "y"}}
   :logger "happy.lucky"
   :platform "clojure"
   :tags {:one 2}
   :breadcrumbs [{:type "http"
                  :level :info
                  :message "yes"
                  :category "maybe"
                  :data {"probably" "no"}
                  :timestamp (Date.)}]
   :server-name "example.com"
   :fingerprints ["{{ default }}" "nice"]
   :extra {:one {:two 2}}
   :transaction "456"})

(def event-with-simple-message
  (assoc event :message "Hello World"))

(defn serialize
  [event]
  (let [serializer (JsonSerializer. (SentryOptions.))
        sentry-event (#'sut/map->event event)
        string-writer (StringWriter.)]
    (.serialize serializer sentry-event string-writer)
    string-writer))

(defexpect map->breadcrumb-test
  (expecting "breadcrumbs"
    (let [breadcrumb ^Breadcrumb (#'sut/map->breadcrumb {:type "type" :level :info :message "message" :category "category" :data {:a "b" :c "d"} :timestamp (Date. 1000000000000)})]
      (expect "type" (.getType breadcrumb))
      (expect SentryLevel/INFO (.getLevel breadcrumb))
      (expect "message" (.getMessage breadcrumb))
      (expect "category" (.getCategory breadcrumb))
      (expect {"a" "b" "c" "d"} (.getData breadcrumb))
      (expect (Date. 1000000000000) (.getTimestamp breadcrumb)))))

(defexpect map->user-test
  (expecting "a user"
    (let [user ^User (#'sut/map->user {:email "foo@bar.com" :id "id" :username "username" :ip-address "10.0.0.0" :data {"a" "b"}})]
      (expect "foo@bar.com" (.getEmail user))
      (expect "id" (.getId user))
      (expect "username" (.getUsername user))
      (expect "10.0.0.0" (.getIpAddress user))
      (expect {"a" "b"} (.getData user)))))

(defexpect map->request-test
  (expecting "a request"
    (let [request ^Request (#'sut/map->request {:url "http://example.com"
                                                :method "GET"
                                                :query-string "?foo=bar"
                                                :headers {"Cookie" "cookie1=foo;cookie2=bar"
                                                          "X-Clacks-Overhead" "Terry Pratchett"
                                                          "X-w00t" "ftw!"}
                                                :env {"a" "b"}
                                                :data {"c" "d"}
                                                :other {"x" "y"}})]
      (expect "http://example.com" (.getUrl request))
      (expect "GET" (.getMethod request))
      (expect "?foo=bar" (.getQueryString request))
      (expect "cookie1=foo;cookie2=bar" (.getCookies request))
      (expect {"Cookie" "cookie1=foo;cookie2=bar" "X-Clacks-Overhead" "Terry Pratchett" "X-w00t" "ftw!"} (.getHeaders request))
      (expect {"a" "b"} (.getEnvs request))
      (expect {"c" "d"} (.getData request))
      (expect {"x" "y"} (.getOthers request)))))

(defn strip-timestamp
  [output]
  (let [result (-> (json/parse-string (str output))
                   (assoc-in ["sdk" "version"] "blah")
                   (dissoc "timestamp"))]
    (assoc result "breadcrumbs" (map #(dissoc % "timestamp") (get result "breadcrumbs")))))

(defexpect map->event-test
  (expecting "a regular event"
    (let [output (serialize event)
          actual (strip-timestamp output)]
      (expect {"release" "v1.0.0"
               "event_id" "4c4fbea957a74c99808d2284306e6c98"
               "dist" "arch"
               "message" {"message" "ok" "params" ["foo" "bar"]}
               "tags" {"one" "2"}
               "level" "info"
               "server_name" "example.com"
               "logger" "happy.lucky"
               "environment" "production"
               "user" {"email" "foo@bar.com"
                       "id" "id"
                       "ip_address" "10.0.0.0"
                       "data" {"a" "b"}
                       "username" "username"}
               "request" {"cookies" "cookie1=foo;cookie2=bar"
                          "env" {"a" "b"}
                          "headers" {"Cookie" "cookie1=foo;cookie2=bar" "X-Clacks-Overhead" "Terry Pratchett", "X-w00t" "ftw!"}
                          "method" "GET"
                          "data" {"c" "d"}
                          "other" {"x" "y"}
                          "query_string" "?foo=bar"
                          "url" "http://example.com"}
               "transaction" "456"
               "extra" {"one" {"two" 2}}
               "platform" "clojure"
               "contexts" {}
               "breadcrumbs" [{"type" "http"
                               "level" "info"
                               "message" "yes"
                               "category" "maybe"
                               "data" {"probably" "no"}}]
               "sdk" {"version" "blah"}
               "fingerprint" ["{{ default }}" "nice"]}
        actual))))

(defexpect map->event-with-simple-message-test
  (expecting "a regular event"
    (let [output (serialize event-with-simple-message)
          actual (strip-timestamp output)]
      (expect {"release" "v1.0.0"
               "event_id" "4c4fbea957a74c99808d2284306e6c98"
               "dist" "arch"
               "message" {"message" "Hello World"}
               "tags" {"one" "2"}
               "level" "info"
               "server_name" "example.com"
               "logger" "happy.lucky"
               "environment" "production"
               "user" {"email" "foo@bar.com"
                       "id" "id"
                       "ip_address" "10.0.0.0"
                       "data" {"a" "b"}
                       "username" "username"}
               "request" {"cookies" "cookie1=foo;cookie2=bar"
                          "env" {"a" "b"}
                          "headers" {"Cookie" "cookie1=foo;cookie2=bar" "X-Clacks-Overhead" "Terry Pratchett", "X-w00t" "ftw!"}
                          "method" "GET"
                          "data" {"c" "d"}
                          "other" {"x" "y"}
                          "query_string" "?foo=bar"
                          "url" "http://example.com"}
               "transaction" "456"
               "extra" {"one" {"two" 2}}
               "platform" "clojure"
               "contexts" {}
               "breadcrumbs" [{"type" "http"
                               "level" "info"
                               "message" "yes"
                               "category" "maybe"
                               "data" {"probably" "no"}}]
               "sdk" {"version" "blah"}
               "fingerprint" ["{{ default }}" "nice"]}
        actual))))

(defexpect map->event-test-with-ex-info
  (expecting "an ex-info event"
    (let [output (serialize (assoc event :throwable (ex-info "bad stuff"
                                                             {:data 1}
                                                             (ex-info "this is a transitive cause"
                                                                      {:data 2}
                                                                      (RuntimeException.
                                                                       "this is a non-info transitive cause"
                                                                       (ex-info "this is the root cause"
                                                                                {:data 3}))))))
          actual (strip-timestamp output)]
      (expect {"release" "v1.0.0"
               "event_id" "4c4fbea957a74c99808d2284306e6c98"
               "dist" "arch"
               "message" {"message" "ok" "params" ["foo" "bar"]}
               "tags" {"one" "2"}
               "level" "info"
               "server_name" "example.com"
               "logger" "happy.lucky"
               "environment" "production"
               "user" {"email" "foo@bar.com"
                       "id" "id"
                       "ip_address" "10.0.0.0"
                       "data" {"a" "b"}
                       "username" "username"}
               "request" {"cookies" "cookie1=foo;cookie2=bar"
                          "env" {"a" "b"}
                          "headers" {"Cookie" "cookie1=foo;cookie2=bar" "X-Clacks-Overhead" "Terry Pratchett", "X-w00t" "ftw!"}
                          "method" "GET"
                          "data" {"c" "d"}
                          "other" {"x" "y"}
                          "query_string" "?foo=bar"
                          "url" "http://example.com"}
               "transaction" "456"
               "extra" {"one" {"two" 2}
                        "ex-data" {"data" 1}
                        "ex-data, cause 1: this is a transitive cause" {"data" 2}
                        "ex-data, cause 3: this is the root cause" {"data" 3}}
               "platform" "clojure"
               "contexts" {}
               "breadcrumbs" [{"type" "http"
                               "level" "info"
                               "message" "yes"
                               "category" "maybe"
                               "data" {"probably" "no"}}]
               "sdk" {"version" "blah"}
               "fingerprint" ["{{ default }}" "nice"]}
        actual))))

(defexpect map->event-test-with-extra-data
  (expecting "an event with deeply-nested extra data"
    (let [output (serialize (-> event
                                (assoc-in [:extra :one :three] {:iii [3]})
                                (assoc-in [:extra :one :four] {:iv 4})
                                (assoc-in [:extra :welp] {:nope "ok"})))
          extra {"one" {"two" 2
                        "three" {"iii" [3]}
                        "four" {"iv" 4}}
                 "welp" {"nope" "ok"}}
          actual (strip-timestamp output)]
      (expect {"release" "v1.0.0"
               "event_id" "4c4fbea957a74c99808d2284306e6c98"
               "dist" "arch"
               "message" {"message" "ok" "params" ["foo" "bar"]}
               "tags" {"one" "2"}
               "level" "info"
               "server_name" "example.com"
               "logger" "happy.lucky"
               "environment" "production"
               "user" {"email" "foo@bar.com"
                       "id" "id"
                       "ip_address" "10.0.0.0"
                       "data" {"a" "b"}
                       "username" "username"}
               "request" {"cookies" "cookie1=foo;cookie2=bar"
                          "env" {"a" "b"}
                          "headers" {"Cookie" "cookie1=foo;cookie2=bar" "X-Clacks-Overhead" "Terry Pratchett", "X-w00t" "ftw!"}
                          "method" "GET"
                          "data" {"c" "d"}
                          "other" {"x" "y"}
                          "query_string" "?foo=bar"
                          "url" "http://example.com"}
               "transaction" "456"
               "extra" extra
               "platform" "clojure"
               "contexts" {}
               "breadcrumbs" [{"type" "http"
                               "level" "info"
                               "message" "yes"
                               "category" "maybe"
                               "data" {"probably" "no"}}]
               "sdk" {"version" "blah"}
               "fingerprint" ["{{ default }}" "nice"]}
        actual))))

(def ^:private sentry-options #'sut/sentry-options)

(defrecord SomeEventProcessor
           []
  EventProcessor)

(defexpect sentry-options-tests
  (expecting "sentry options test"
    (let [sentry-options ^SentryOptions (sentry-options "http://www.example.com" {:environment "production"
                                                                                  :release "1.1"
                                                                                  :dist "x86"
                                                                                  :server-name "host1"
                                                                                  :shutdown-timeout-millis 1000
                                                                                  :in-app-includes ["com.includes" "com.includes2"]
                                                                                  :in-app-excludes ["com.excludes" "com.excludes2"]
                                                                                  :ignored-exceptions-for-type ["java.io.IOException" "java.lang.RuntimeException"]
                                                                                  :debug true
                                                                                  :enable-uncaught-exception-handler false
                                                                                  :trace-options-requests false
                                                                                  :logs-enabled true
                                                                                  :before-send-log-fn (fn [event] (.setBody event "new message body") event)
                                                                                  :before-send-metric-fn (fn [metric _hint] metric)
                                                                                  :instrumenter :otel
                                                                                  :event-processors [(SomeEventProcessor.)]
                                                                                  :enabled false})]
      (expect "http://www.example.com" (.getDsn sentry-options))
      (expect "production" (.getEnvironment sentry-options))
      (expect "1.1" (.getRelease sentry-options))
      (expect "x86" (.getDist sentry-options))
      (expect "host1" (.getServerName sentry-options))
      (expect 1000 (.getShutdownTimeoutMillis sentry-options))
      (expect "com.includes" (first (.getInAppIncludes sentry-options)))
      (expect "com.includes2" (second (.getInAppIncludes sentry-options)))
      (expect "com.excludes" (first (.getInAppExcludes sentry-options)))
      (expect "com.excludes2" (second (.getInAppExcludes sentry-options)))
      (expect (isa? (first (.getIgnoredExceptionsForType sentry-options)) java.io.IOException))
      (expect (isa? (second (.getIgnoredExceptionsForType sentry-options)) java.lang.RuntimeException))
      (expect (.isDebug sentry-options))
      (expect false (.isEnableUncaughtExceptionHandler sentry-options))
      (expect false (.isTraceOptionsRequests sentry-options))
      (expect true (.isEnabled (.getLogs sentry-options)))
      (expect false (nil? (.getBeforeSend (.getLogs sentry-options))))
      (expect false (nil? (.getBeforeSend (.getMetrics sentry-options))))
      (expect Instrumenter/OTEL (.getInstrumenter sentry-options))
      (expect (instance? SomeEventProcessor (last (.getEventProcessors sentry-options))))
      (expect false (.isEnabled sentry-options)))))

(defexpect sentry-enabled-tests
  (expecting
    "sentry is enabled by default"
    (let [sentry-options ^SentryOptions (sentry-options "http://www.example.com")]
      (expect true (.isEnabled sentry-options))))
  (expecting
    "sentry is enabled"
    (let [sentry-options ^SentryOptions (sentry-options "http://www.example.com" {:enabled true})]
      (expect true (.isEnabled sentry-options))))
  (expecting
    "sentry is disabled"
    (let [sentry-options ^SentryOptions (sentry-options "http://www.example.com" {:enabled false})]
      (expect false (.isEnabled sentry-options)))))
