(ns raven-clj.internal-test
  (:require [cheshire.core :as json]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clojure.test :refer :all]
            [raven-clj.internal :refer :all])
  (:import (java.io ByteArrayOutputStream)
           (java.util UUID)
           (com.fasterxml.jackson.core JsonFactory)
           (com.getsentry.raven.dsn Dsn)
           (com.getsentry.raven.event EventBuilder)))

(deftest interface-test
  (let [interface (->CljInterface "woo" {:blah 1})]
    (is (= "woo" (.getInterfaceName interface)))))

(deftest binding-test
  (let [binding   (->CljInterfaceBinding)
        interface (->CljInterface :woo {:blah 1})
        factory   (JsonFactory.)
        output    (ByteArrayOutputStream.)]
    (with-open [generator (.createGenerator factory output)]
      (.writeInterface binding generator interface))
    (is (= {"blah" 1}
           (-> output .toString json/parse-string)))))

(deftest factory-test
  (let [dsn        (Dsn. "https://111:222@sentry.io/100")
        marshaller (.createMarshaller factory dsn)
        output     (ByteArrayOutputStream.)
        id         (UUID/fromString "4c4fbea9-57a7-4c99-808d-2284306e6c98")
        event      (.. (EventBuilder. id)
                       (withTimestamp (tc/to-date (t/date-time 2016 9 6)))
                       (withServerName "yay")
                       (withSentryInterface (->CljInterface "woo" {:blah 1}))
                       (build))]
    (.setCompression marshaller false)
    (.marshall marshaller event output)
    (is (= {"release"     nil
            "event_id"    "4c4fbea957a74c99808d2284306e6c98"
            "message"     nil
            "woo"         {"blah" 1}
            "tags"        {}
            "timestamp"   "2016-09-06T00:00:00"
            "level"       nil
            "server_name" "yay"
            "logger"      nil
            "environment" nil
            "culprit"     nil
            "extra"       {}
            "checksum"    nil
            "platform"    "java"
            "sdk"         {"name"    "raven-java"
                           "version" "blah"}}
           (-> output .toString json/parse-string
               (assoc-in ["sdk" "version"] "blah"))))))
