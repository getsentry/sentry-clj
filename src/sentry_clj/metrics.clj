(ns sentry-clj.metrics
  "Metrics integration with Sentry.

   Provides functions for sending counters, gauges, and distributions:
   - `increment` - Track incrementing values (e.g., button clicks, function calls)
   - `gauge` - Track values that go up and down (e.g., memory usage, queue depth)
   - `distribution` - Track value distributions (e.g., response times)

   ## Basic Usage
   ```clojure
   (increment \"page_view\")
   (gauge \"queue_depth\" 42.0)
   (distribution \"response_time\" 187.5 :millisecond)
   ```

   ## With Attributes
   ```clojure
   (distribution \"page_load\" 1.0 :millisecond {:browser \"Firefox\"})
   (increment \"api_call\" 1.0 nil {:endpoint \"/users\"})
   ```"
  (:import [io.sentry Sentry SentryAttributes SentryAttribute]
           [io.sentry.metrics IMetricsApi SentryMetricsParameters MetricsUnit$Duration MetricsUnit$Information MetricsUnit$Fraction]))

(set! *warn-on-reflection* true)

(defn- get-metrics-api
  "Returns the IMetricsApi instance from Sentry."
  ^IMetricsApi []
  (Sentry/metrics))

(def ^:private unit-map
  {:none nil
   ;; Duration
   :nanosecond MetricsUnit$Duration/NANOSECOND
   :microsecond MetricsUnit$Duration/MICROSECOND
   :millisecond MetricsUnit$Duration/MILLISECOND
   :second MetricsUnit$Duration/SECOND
   :minute MetricsUnit$Duration/MINUTE
   :hour MetricsUnit$Duration/HOUR
   :day MetricsUnit$Duration/DAY
   :week MetricsUnit$Duration/WEEK
   ;; Information
   :bit MetricsUnit$Information/BIT
   :byte MetricsUnit$Information/BYTE
   :kilobyte MetricsUnit$Information/KILOBYTE
   :kibibyte MetricsUnit$Information/KIBIBYTE
   :megabyte MetricsUnit$Information/MEGABYTE
   :mebibyte MetricsUnit$Information/MEBIBYTE
   :gigabyte MetricsUnit$Information/GIGABYTE
   :gibibyte MetricsUnit$Information/GIBIBYTE
   :terabyte MetricsUnit$Information/TERABYTE
   :tebibyte MetricsUnit$Information/TEBIBYTE
   :petabyte MetricsUnit$Information/PETABYTE
   :pebibyte MetricsUnit$Information/PEBIBYTE
   :exabyte MetricsUnit$Information/EXABYTE
   :exbibyte MetricsUnit$Information/EXBIBYTE
   ;; Fraction
   :ratio MetricsUnit$Fraction/RATIO
   :percent MetricsUnit$Fraction/PERCENT})

(defn- keyword->unit
  "Converts a keyword to a MetricsUnit string constant.
   Accepts keywords from unit-map, raw strings, or nil.
   Both :none and nil mean no unit."
  ^String [unit]
  (cond
    (nil? unit) nil
    (string? unit) unit
    (keyword? unit) (if (contains? unit-map unit)
                      (get unit-map unit)
                      (throw (IllegalArgumentException. (str "Unknown metric unit: " unit))))
    :else (throw (IllegalArgumentException. (str "Invalid metric unit type: " (type unit))))))

(defn- attrs->params
  "Converts a Clojure map of attributes to SentryMetricsParameters.
   Reuses the SentryAttribute type detection pattern from sentry-clj.logging."
  ^SentryMetricsParameters [attrs]
  (let [attributes (reduce-kv
                    (fn [acc k v]
                      (let [attr-name (name k)
                            attr (cond
                                   (string? v) (SentryAttribute/stringAttribute attr-name v)
                                   (boolean? v) (SentryAttribute/booleanAttribute attr-name v)
                                   (integer? v) (if (<= Integer/MIN_VALUE v Integer/MAX_VALUE)
                                                  (SentryAttribute/integerAttribute attr-name (int v))
                                                  (SentryAttribute/doubleAttribute attr-name (double v)))
                                   (or (double? v) (float? v)) (SentryAttribute/doubleAttribute attr-name (double v))
                                   :else (SentryAttribute/named attr-name v))]
                        (conj acc attr)))
                    []
                    attrs)]
    (SentryMetricsParameters/create
     (SentryAttributes/of (into-array SentryAttribute attributes)))))

(defn increment
  "Increment a counter metric.
   Wraps Sentry.metrics().count().
   Named `increment` to avoid clash with clojure.core/count.

   - `(increment name)` — increment by 1.0
   - `(increment name value)` — increment by value
   - `(increment name value unit)` — with unit keyword or string
   - `(increment name value unit attrs)` — with attributes map"
  ([metric-name]
   (.count (get-metrics-api) ^String metric-name))
  ([metric-name value]
   (.count (get-metrics-api) ^String metric-name ^Double (double value)))
  ([metric-name value unit]
   (.count (get-metrics-api) ^String metric-name ^Double (double value) ^String (keyword->unit unit)))
  ([metric-name value unit attrs]
   (.count (get-metrics-api) ^String metric-name ^Double (double value) ^String (keyword->unit unit) ^SentryMetricsParameters (attrs->params attrs))))

(defn gauge
  "Record a gauge metric value.

   - `(gauge name value)` — record value
   - `(gauge name value unit)` — with unit keyword or string
   - `(gauge name value unit attrs)` — with attributes map"
  ([metric-name value]
   (.gauge (get-metrics-api) ^String metric-name ^Double (double value)))
  ([metric-name value unit]
   (.gauge (get-metrics-api) ^String metric-name ^Double (double value) ^String (keyword->unit unit)))
  ([metric-name value unit attrs]
   (.gauge (get-metrics-api) ^String metric-name ^Double (double value) ^String (keyword->unit unit) ^SentryMetricsParameters (attrs->params attrs))))

(defn distribution
  "Record a distribution metric value.

   - `(distribution name value)` — record value
   - `(distribution name value unit)` — with unit keyword or string
   - `(distribution name value unit attrs)` — with attributes map"
  ([metric-name value]
   (.distribution (get-metrics-api) ^String metric-name ^Double (double value)))
  ([metric-name value unit]
   (.distribution (get-metrics-api) ^String metric-name ^Double (double value) ^String (keyword->unit unit)))
  ([metric-name value unit attrs]
   (.distribution (get-metrics-api) ^String metric-name ^Double (double value) ^String (keyword->unit unit) ^SentryMetricsParameters (attrs->params attrs))))
