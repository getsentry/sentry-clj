(ns sentry-clj.logging-test
  (:require
    [expectations.clojure.test :refer [defexpect expect expecting]]
    [sentry-clj.logging :as sut])
  (:import
    [io.sentry Sentry SentryOptions SentryLogLevel]
    [io.sentry.logger ILoggerApi SentryLogParameters]))

(defn ^:private get-test-logger-options ^SentryOptions
  ([] (get-test-logger-options {}))
  ([{:keys [dsn logs-enabled] :as _opts}]
   (let [sentry-options (SentryOptions.)]
     (.setDsn sentry-options (or dsn "https://key@sentry.io/proj"))
     (.setEnvironment sentry-options "test")
     (.setRelease sentry-options "release@1.0.0")
     (when logs-enabled
       (-> sentry-options .getLogs (.setEnabled true)))
     sentry-options)))

(defn ^:private setup-test-sentry!
  "Initializes Sentry for testing and returns the options used."
  ([] (setup-test-sentry! {}))
  ([opts]
   (let [sentry-options (get-test-logger-options opts)]
     (Sentry/init ^SentryOptions sentry-options)
     sentry-options)))

(defexpect level-specific-function-mock-test
  (setup-test-sentry!)
  (let [calls (atom [])]
    (with-redefs [sut/get-sentry-logger
                  (fn []
                    (reify ILoggerApi
                      (trace [_ message params]
                        (swap! calls conj [:trace message (vec params)]))

                      (debug [_ message params]
                        (swap! calls conj [:debug message (vec params)]))

                      (info [_ message params]
                        (swap! calls conj [:info message (vec params)]))

                      (warn [_ message params]
                        (swap! calls conj [:warn message (vec params)]))

                      (error [_ message params]
                        (swap! calls conj [:error message (vec params)]))

                      (fatal [_ message params]
                        (swap! calls conj [:fatal message (vec params)]))))]
      (doseq [[log-fn-name log-fn] {:trace sut/trace
                                    :debug sut/debug
                                    :info sut/info
                                    :warn sut/warn
                                    :error sut/error
                                    :fatal sut/fatal}]
        (expecting "log specific function works with formatted message"
          (reset! calls [])
          (log-fn "message: %s %d" "arg1" 42)
          (expect 1 (count @calls))
          (expect [log-fn-name "message: %s %d" ["arg1" 42]] (first @calls)))

        (expecting "log specific function works with simple message"
          (reset! calls [])
          (log-fn "test message")
          (expect 1 (count @calls))
          (expect [log-fn-name "test message" []] (first @calls)))))))


(defexpect level-specific-function-test
  (setup-test-sentry!)
  (doseq [log-fn [sut/trace sut/debug sut/info sut/warn sut/error sut/fatal]]
    (expecting "log specific function works with formatted message"
      (expect nil? (log-fn "test message: %s %d" "arg1" 42)))

    (expecting "log specific function works with simple message"
      (expect nil? (log-fn "test message")))))

(defexpect log-with-level-unknown-log-level-test
  (expecting "throws IllegalArgumentException for unknown log level keyword"
    (expect IllegalArgumentException (sut/log :unknown "message"))))


(defexpect keyword->sentry-level-test
  (expecting "converts valid keywords to SentryLogLevel enums"
    (expect SentryLogLevel/TRACE (#'sut/keyword->sentry-level :trace))
    (expect SentryLogLevel/DEBUG (#'sut/keyword->sentry-level :debug))
    (expect SentryLogLevel/INFO (#'sut/keyword->sentry-level :info))
    (expect SentryLogLevel/WARN (#'sut/keyword->sentry-level :warn))
    (expect SentryLogLevel/ERROR (#'sut/keyword->sentry-level :error))
    (expect SentryLogLevel/FATAL (#'sut/keyword->sentry-level :fatal)))

  (expecting "passes through existing SentryLogLevel instances"
    (expect SentryLogLevel/INFO (#'sut/keyword->sentry-level SentryLogLevel/INFO))
    (expect SentryLogLevel/ERROR (#'sut/keyword->sentry-level SentryLogLevel/ERROR)))

  (expecting "throws IllegalArgumentException for invalid keywords"
    (expect IllegalArgumentException (#'sut/keyword->sentry-level :invalid))))
