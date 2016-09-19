(ns raven-clj.core-test
  (:require [cheshire.core :as json]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clojure.test :refer :all]
            [raven-clj.core :as core :refer :all]
            [raven-clj.internal :as internal])
  (:import (java.io ByteArrayOutputStream)
           (java.util UUID)
           (com.fasterxml.jackson.core JsonFactory)
           (com.getsentry.raven.dsn Dsn)
           (com.getsentry.raven.event BreadcrumbBuilder
                                      Event$Level
                                      EventBuilder)))

(deftest keyword->level-test
  (is (= Event$Level/DEBUG
         (#'core/keyword->level :debug)))
  (is (= Event$Level/INFO
         (#'core/keyword->level :info)))
  (is (= Event$Level/WARNING
         (#'core/keyword->level :warning)))
  (is (= Event$Level/ERROR
         (#'core/keyword->level :error)))
  (is (= Event$Level/FATAL
         (#'core/keyword->level :fatal))))

(def id
  (UUID/fromString "4c4fbea9-57a7-4c99-808d-2284306e6c98"))

(def event
  {:event-id     id
   :level        :info
   :release      "v1.0.0"
   :environment  "qa"
   :logger       "happy.lucky"
   :timestamp    (t/date-time 2016 9 7)
   :platform     "clojure"
   :tags         {:one 2}
   :culprit      "123"
   :extra        {:one {:two 2}}
   :checksum-for "yes this is"
   :server-name  "example.com"
   :interfaces   {:user {:id 100}}
   :breadcrumbs  [{:type      "woo"
                   :timestamp (t/date-time 2016 9 6)
                   :level     "ok"
                   :message   "yes"
                   :category  "maybe"
                   :data      {"probably" "no"}}]
   :message      "ok"})

(deftest map->event-test
  (let [dsn        (Dsn. "https://111:222@sentry.io/100")
        marshaller (.createMarshaller internal/factory dsn)]
    (.setCompression marshaller false)

    (testing "a regular event"
      (let [output     (ByteArrayOutputStream.)]
        (.marshall marshaller (#'core/map->event event) output)
        (is (= {"release"     "v1.0.0"
                "event_id"    "4c4fbea957a74c99808d2284306e6c98"
                "message"     "ok"
                "user"        {"id" 100}
                "tags"        {"one" "2"}
                "timestamp"   "2016-09-07T00:00:00"
                "level"       "info"
                "server_name" "example.com"
                "logger"      "happy.lucky"
                "environment" "qa"
                "culprit"     "123"
                "extra"       {"one" [["two" 2]]}
                "checksum"    "BD285A21"
                "platform"    "clojure"
                "breadcrumbs" {"values" [{"timestamp" 1473120000
                                          "type"      "woo"
                                          "level"     "ok"
                                          "message"   "yes"
                                          "category"  "maybe"
                                          "data"      {"probably" "no"}}]}}
               (-> output .toString json/parse-string)))))

    (testing "an ex-info event"
      (let [output     (ByteArrayOutputStream.)]
        (.marshall marshaller (-> event
                                  (assoc :throwable (ex-info "bad stuff"
                                                             {:ex-info 2}))
                                  (#'core/map->event)) output)
        (is (= {"release"     "v1.0.0"
                "event_id"    "4c4fbea957a74c99808d2284306e6c98"
                "message"     "ok"
                "user"        {"id" 100}
                "tags"        {"one" "2"}
                "timestamp"   "2016-09-07T00:00:00"
                "level"       "info"
                "server_name" "example.com"
                "logger"      "happy.lucky"
                "environment" "qa"
                "culprit"     "123"
                "extra"       {"one" [["two" 2]], "ex-info" 2}
                "checksum"    "BD285A21"
                "platform"    "clojure"
                "breadcrumbs" {"values" [{"timestamp" 1473120000
                                          "type"      "woo"
                                          "level"     "ok"
                                          "message"   "yes"
                                          "category"  "maybe"
                                          "data"      {"probably" "no"}}]}}
               (-> output .toString json/parse-string
                   (dissoc "sentry.interfaces.Exception"))))))))
