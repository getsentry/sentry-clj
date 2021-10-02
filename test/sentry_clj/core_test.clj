(ns sentry-clj.core-test
  (:require
   [cheshire.core :as json]
   [expectations.clojure.test :refer [defexpect expect expecting]]
   [sentry-clj.core :as sut])
  (:import
   [io.sentry
    GsonSerializer
    SentryOptions
    SentryLevel]
   [java.io StringWriter]
   [java.util UUID Date]))

(defexpect keyword->level-test
  (expecting
   "keyword maps to level correctly"
   (expect SentryLevel/DEBUG (#'sut/keyword->level :debug))
   (expect SentryLevel/INFO (#'sut/keyword->level :info))
   (expect SentryLevel/WARNING (#'sut/keyword->level :warning))
   (expect SentryLevel/ERROR (#'sut/keyword->level :error))
   (expect SentryLevel/FATAL (#'sut/keyword->level :fatal))))

(def event
  {:event-id     (UUID/fromString "4c4fbea9-57a7-4c99-808d-2284306e6c98")
   :message      {:message "ok" :params ["foo" "bar"]}
   :level        :info
   :dist         "arch"
   :release      "v1.0.0"
   :environment  "production"
   :user         {:email "foo@bar.com"
                  :id "id"
                  :username "username"
                  :ip-address "10.0.0.0"
                  :other {"a" "b"}}
   :request {:url "http://foobar.com"
             :method "GET"
             :query-string "?foo=bar"
             :data "data"
             :cookies "cookie1=foo;cookie2=bar"
             :headers {"X-Clacks-Overhead" "Terry Pratchett"
                       "X-w00t" "ftw!"}
             :env {"a" "b"}
             :other {"c" "d"}}
   :logger       "happy.lucky"
   :platform     "clojure"
   :tags         {:one 2}
   :breadcrumbs  [{:type      "http"
                   :level     :info
                   :message   "yes"
                   :category  "maybe"
                   :data      {"probably" "no"}
                   :timestamp (Date.)}]
   :server-name  "example.com"
   :fingerprints ["{{ default }}" "nice"]
   :extra        {:one {:two 2}}
   :transaction  "456"})

(def event-with-simple-message
  (assoc event :message "Hello World"))

(defn serialize
  [event]
  (let [serializer (GsonSerializer. (SentryOptions.))
        sentry-event (#'sut/map->event event)
        string-writer (StringWriter.)]
    (.serialize serializer sentry-event string-writer)
    string-writer))

(defexpect map->breadcrumb-test
  (expecting
   "breadcrumbs"
   (let [breadcrumb (#'sut/map->breadcrumb {:type "type" :level :info :message "message" :category "category" :data {:a "b" :c "d"} :timestamp (Date. 1000000000000)})]
     (expect "type" (.getType breadcrumb))
     (expect SentryLevel/INFO (.getLevel breadcrumb))
     (expect "message" (.getMessage breadcrumb))
     (expect "category" (.getCategory breadcrumb))
     (expect {"a" "b" "c" "d"} (.getData breadcrumb))
     (expect (Date. 1000000000000) (.getTimestamp breadcrumb)))))

(defexpect map->user-test
  (expecting
   "a user"
   (let [user (#'sut/map->user {:email "foo@bar.com" :id "id" :username "username" :ip-address "10.0.0.0" :other {"a" "b"}})]
     (expect "foo@bar.com" (.getEmail user))
     (expect "id" (.getId user))
     (expect "username" (.getUsername user))
     (expect "10.0.0.0" (.getIpAddress user))
     (expect {"a" "b"} (.getOthers user)))))

(defexpect map->request-test
  (expecting
   "a request"
   (let [request (#'sut/map->request {:url "http://foobar.com"
                                      :method "GET"
                                      :query-string "?foo=bar"
                                      :data "data"
                                      :cookies "cookie1=foo;cookie2=bar"
                                      :headers {"X-Clacks-Overhead" "Terry Pratchett"
                                                "X-w00t" "ftw!"}
                                      :env {"a" "b"}
                                      :other {"c" "d"}})]
     (expect "http://foobar.com" (.getUrl request))
     (expect "GET" (.getMethod request))
     (expect "?foo=bar" (.getQueryString request))
     (expect "data" (.getData request))
     (expect "cookie1=foo;cookie2=bar" (.getCookies request))
     (expect {"X-Clacks-Overhead" "Terry Pratchett" "X-w00t" "ftw!"} (.getHeaders request))
     (expect {"a" "b"} (.getEnvs request))
     (expect {"c" "d"} (.getOthers request)))))

(defn strip-timestamp
  [output]
  (let [result (-> (json/parse-string (str output))
                   (assoc-in ["sdk" "version"] "blah")
                   (dissoc "timestamp"))]
    (assoc result "breadcrumbs" (map #(dissoc % "timestamp") (get result "breadcrumbs")))))

(defexpect map->event-test
  (expecting
   "a regular event"
   (let [output (serialize event)
         actual (strip-timestamp output)]
     (expect {"release"     "v1.0.0"
              "event_id"    "4c4fbea957a74c99808d2284306e6c98"
              "dist"        "arch"
              "message"     {"message" "ok" "params" ["foo" "bar"]}
              "tags"        {"one" "2"}
              "level"       "info"
              "server_name" "example.com"
              "logger"      "happy.lucky"
              "environment" "production"
              "user"        {"email" "foo@bar.com"
                             "id" "id"
                             "ip_address" "10.0.0.0"
                             "other" {"a" "b"}
                             "username" "username"}
              "request"     {"cookies" "cookie1=foo;cookie2=bar"
                             "data" "data"
                             "env" {"a" "b"}
                             "headers" {"X-Clacks-Overhead" "Terry Pratchett", "X-w00t" "ftw!"}
                             "method" "GET"
                             "other" {"c" "d"}
                             "query_string" "?foo=bar"
                             "url" "http://foobar.com"}
              "transaction" "456"
              "extra"       {"one" {"two" 2}}
              "platform"    "clojure"
              "contexts"    {}
              "breadcrumbs" [{"type"      "http"
                              "level"     "info"
                              "message"   "yes"
                              "category"  "maybe"
                              "data"      {"probably" "no"}}]
              "sdk"         {"version" "blah"}
              "fingerprint" ["{{ default }}" "nice"]}
             actual))))

(defexpect map->event-with-simple-message-test
  (expecting
   "a regular event"
   (let [output (serialize event-with-simple-message)
         actual (strip-timestamp output)]
     (expect {"release"     "v1.0.0"
              "event_id"    "4c4fbea957a74c99808d2284306e6c98"
              "dist"        "arch"
              "message"     {"message" "Hello World"}
              "tags"        {"one" "2"}
              "level"       "info"
              "server_name" "example.com"
              "logger"      "happy.lucky"
              "environment" "production"
              "user"        {"email" "foo@bar.com"
                             "id" "id"
                             "ip_address" "10.0.0.0"
                             "other" {"a" "b"}
                             "username" "username"}
              "request"     {"cookies" "cookie1=foo;cookie2=bar"
                             "data" "data"
                             "env" {"a" "b"}
                             "headers" {"X-Clacks-Overhead" "Terry Pratchett", "X-w00t" "ftw!"}
                             "method" "GET"
                             "other" {"c" "d"}
                             "query_string" "?foo=bar"
                             "url" "http://foobar.com"}
              "transaction" "456"
              "extra"       {"one" {"two" 2}}
              "platform"    "clojure"
              "contexts"    {}
              "breadcrumbs" [{"type"      "http"
                              "level"     "info"
                              "message"   "yes"
                              "category"  "maybe"
                              "data"      {"probably" "no"}}]
              "sdk"         {"version" "blah"}
              "fingerprint" ["{{ default }}" "nice"]}
             actual))))

(defexpect map->event-test-with-ex-info
  (expecting
   "an ex-info event"
   (let [output (serialize (assoc event :throwable (ex-info "bad stuff" {:ex-info 2})))
         actual (strip-timestamp output)]
     (expect {"release"     "v1.0.0"
              "event_id"    "4c4fbea957a74c99808d2284306e6c98"
              "dist"        "arch"
              "message"     {"message" "ok" "params" ["foo" "bar"]}
              "tags"        {"one" "2"}
              "level"       "info"
              "server_name" "example.com"
              "logger"      "happy.lucky"
              "environment" "production"
              "user"        {"email" "foo@bar.com"
                             "id" "id"
                             "ip_address" "10.0.0.0"
                             "other" {"a" "b"}
                             "username" "username"}
              "request"     {"cookies" "cookie1=foo;cookie2=bar"
                             "data" "data"
                             "env" {"a" "b"}
                             "headers" {"X-Clacks-Overhead" "Terry Pratchett", "X-w00t" "ftw!"}
                             "method" "GET"
                             "other" {"c" "d"}
                             "query_string" "?foo=bar"
                             "url" "http://foobar.com"}
              "transaction" "456"
              "extra"       {"one" {"two" 2}
                             "ex-info" 2}
              "platform"    "clojure"
              "contexts"    {}
              "breadcrumbs" [{"type"      "http"
                              "level"     "info"
                              "message"   "yes"
                              "category"  "maybe"
                              "data"      {"probably" "no"}}]
              "sdk"         {"version" "blah"}
              "fingerprint" ["{{ default }}" "nice"]}
             actual))))

(defexpect map->event-test-with-extra-data
  (expecting
   "an event with deeply-nested extra data"
   (let [output (serialize (-> event
                               (assoc-in [:extra :one :three] {:iii [3]})
                               (assoc-in [:extra :one :four] {:iv 4})
                               (assoc-in [:extra :welp] {:nope "ok"})))
         extra  {"one"  {"two"   2
                         "three" {"iii" [3]}
                         "four"  {"iv" 4}}
                 "welp" {"nope" "ok"}}
         actual (strip-timestamp output)]
     (expect {"release"     "v1.0.0"
              "event_id"    "4c4fbea957a74c99808d2284306e6c98"
              "dist"        "arch"
              "message"     {"message" "ok" "params" ["foo" "bar"]}
              "tags"        {"one" "2"}
              "level"       "info"
              "server_name" "example.com"
              "logger"      "happy.lucky"
              "environment" "production"
              "user"        {"email" "foo@bar.com"
                             "id" "id"
                             "ip_address" "10.0.0.0"
                             "other" {"a" "b"}
                             "username" "username"}
              "request"     {"cookies" "cookie1=foo;cookie2=bar"
                             "data" "data"
                             "env" {"a" "b"}
                             "headers" {"X-Clacks-Overhead" "Terry Pratchett", "X-w00t" "ftw!"}
                             "method" "GET"
                             "other" {"c" "d"}
                             "query_string" "?foo=bar"
                             "url" "http://foobar.com"}
              "transaction" "456"
              "extra"       extra
              "platform"    "clojure"
              "contexts"    {}
              "breadcrumbs" [{"type"      "http"
                              "level"     "info"
                              "message"   "yes"
                              "category"  "maybe"
                              "data"      {"probably" "no"}}]
              "sdk"         {"version" "blah"}
              "fingerprint" ["{{ default }}" "nice"]}
             actual))))
