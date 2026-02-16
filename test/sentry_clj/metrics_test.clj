(ns sentry-clj.metrics-test
  (:require
   [expectations.clojure.test :refer [defexpect expect expecting]]
   [sentry-clj.metrics :as sut])
  (:import
   [io.sentry Sentry SentryOptions]
   [io.sentry.metrics SentryMetricsParameters MetricsUnit$Duration MetricsUnit$Information MetricsUnit$Fraction]))

(defn- get-test-options ^SentryOptions
  []
  (let [sentry-options (SentryOptions.)]
    (.setDsn sentry-options "https://key@sentry.io/proj")
    (.setEnvironment sentry-options "test")
    (.setRelease sentry-options "release@1.0.0")
    sentry-options))

(defn- setup-test-sentry!
  "Initializes Sentry for testing."
  []
  (let [sentry-options (get-test-options)]
    (Sentry/init ^SentryOptions sentry-options)
    sentry-options))

;; Unit keyword mapping tests

(defexpect keyword->unit-test
  (expecting "converts duration keywords correctly"
    (expect MetricsUnit$Duration/NANOSECOND (#'sut/keyword->unit :nanosecond))
    (expect MetricsUnit$Duration/MICROSECOND (#'sut/keyword->unit :microsecond))
    (expect MetricsUnit$Duration/MILLISECOND (#'sut/keyword->unit :millisecond))
    (expect MetricsUnit$Duration/SECOND (#'sut/keyword->unit :second))
    (expect MetricsUnit$Duration/MINUTE (#'sut/keyword->unit :minute))
    (expect MetricsUnit$Duration/HOUR (#'sut/keyword->unit :hour))
    (expect MetricsUnit$Duration/DAY (#'sut/keyword->unit :day))
    (expect MetricsUnit$Duration/WEEK (#'sut/keyword->unit :week)))

  (expecting "converts information keywords correctly"
    (expect MetricsUnit$Information/BIT (#'sut/keyword->unit :bit))
    (expect MetricsUnit$Information/BYTE (#'sut/keyword->unit :byte))
    (expect MetricsUnit$Information/KILOBYTE (#'sut/keyword->unit :kilobyte))
    (expect MetricsUnit$Information/KIBIBYTE (#'sut/keyword->unit :kibibyte))
    (expect MetricsUnit$Information/MEGABYTE (#'sut/keyword->unit :megabyte))
    (expect MetricsUnit$Information/MEBIBYTE (#'sut/keyword->unit :mebibyte))
    (expect MetricsUnit$Information/GIGABYTE (#'sut/keyword->unit :gigabyte))
    (expect MetricsUnit$Information/GIBIBYTE (#'sut/keyword->unit :gibibyte))
    (expect MetricsUnit$Information/TERABYTE (#'sut/keyword->unit :terabyte))
    (expect MetricsUnit$Information/TEBIBYTE (#'sut/keyword->unit :tebibyte))
    (expect MetricsUnit$Information/PETABYTE (#'sut/keyword->unit :petabyte))
    (expect MetricsUnit$Information/PEBIBYTE (#'sut/keyword->unit :pebibyte))
    (expect MetricsUnit$Information/EXABYTE (#'sut/keyword->unit :exabyte))
    (expect MetricsUnit$Information/EXBIBYTE (#'sut/keyword->unit :exbibyte)))

  (expecting "converts fraction keywords correctly"
    (expect MetricsUnit$Fraction/RATIO (#'sut/keyword->unit :ratio))
    (expect MetricsUnit$Fraction/PERCENT (#'sut/keyword->unit :percent)))

  (expecting ":none and nil map to no unit"
    (expect nil (#'sut/keyword->unit :none))
    (expect nil (#'sut/keyword->unit nil)))

  (expecting "raw strings pass through"
    (expect "custom_unit" (#'sut/keyword->unit "custom_unit")))

  (expecting "invalid keyword throws"
    (expect IllegalArgumentException (#'sut/keyword->unit :bogus))))

;; attrs->params tests

(defexpect attrs->params-test
  (expecting "converts a map to SentryMetricsParameters"
    (let [params (#'sut/attrs->params {:endpoint "/users" :method "GET"})]
      (expect true (instance? SentryMetricsParameters params))))

  (expecting "handles various attribute types"
    (let [params (#'sut/attrs->params {:name "test"
                                       :active true
                                       :count 42
                                       :score 98.5})]
      (expect true (instance? SentryMetricsParameters params)))))

;; Smoke tests - verify the public API functions execute without errors.
;; These call the real Sentry SDK (with a test DSN) which silently drops the
;; metrics. We verify no exceptions are thrown and the correct nil return.

(defexpect increment-smoke-test
  (setup-test-sentry!)
  (expecting "increment with name only"
    (expect nil? (sut/increment "page_view")))
  (expecting "increment with name and value"
    (expect nil? (sut/increment "button_click" 2.0)))
  (expecting "increment with name, value, and unit"
    (expect nil? (sut/increment "requests" 1.0 :millisecond)))
  (expecting "increment with name, value, nil unit"
    (expect nil? (sut/increment "requests" 1.0 :none)))
  (expecting "increment with name, value, string unit"
    (expect nil? (sut/increment "requests" 1.0 "custom_unit")))
  (expecting "increment with name, value, unit, and attrs"
    (expect nil? (sut/increment "api_call" 1.0 :none {:endpoint "/users"}))))

(defexpect gauge-smoke-test
  (setup-test-sentry!)
  (expecting "gauge with name and value"
    (expect nil? (sut/gauge "queue_depth" 42.0)))
  (expecting "gauge with name, value, and unit"
    (expect nil? (sut/gauge "memory_usage" 1024.0 :byte)))
  (expecting "gauge with name, value, unit, and attrs"
    (expect nil? (sut/gauge "cpu_usage" 0.85 :ratio {:region "us-east-1"}))))

(defexpect distribution-smoke-test
  (setup-test-sentry!)
  (expecting "distribution with name and value"
    (expect nil? (sut/distribution "response_time" 187.5)))
  (expecting "distribution with name, value, and unit"
    (expect nil? (sut/distribution "response_time" 187.5 :millisecond)))
  (expecting "distribution with name, value, unit, and attrs"
    (expect nil? (sut/distribution "page_load" 1.0 :millisecond {:browser "Firefox" :region "us-east-1"}))))
