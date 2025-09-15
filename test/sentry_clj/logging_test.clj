(ns sentry-clj.logging-test
  (:require
   [expectations.clojure.test :refer [defexpect expect expecting]]
   [sentry-clj.logging :as sut])
  (:import
   [io.sentry Sentry SentryInstantDate SentryOptions SentryLogLevel SentryDate SentryAttributes SentryAttribute]
   [io.sentry.logger ILoggerApi SentryLogParameters]))

(defn- get-test-logger-options ^SentryOptions
  ([] (get-test-logger-options {}))
  ([{:keys [dsn logs-enabled] :as _opts}]
   (let [sentry-options (SentryOptions.)]
     (.setDsn sentry-options (or dsn "https://key@sentry.io/proj"))
     (.setEnvironment sentry-options "test")
     (.setRelease sentry-options "release@1.0.0")
     (when logs-enabled
       (-> sentry-options .getLogs (.setEnabled true)))
     sentry-options)))

(defn- setup-test-sentry!
  "Initializes Sentry for testing and returns the options used."
  ([] (setup-test-sentry! {}))
  ([opts]
   (let [sentry-options (get-test-logger-options opts)]
     (Sentry/init ^SentryOptions sentry-options)
     sentry-options)))

(defn- mock-logger
  []
  (let [calls (atom [])]
    {:logger-fn (fn []
                  (proxy [ILoggerApi] []
                    (trace [message params]
                      (swap! calls conj [:trace message (vec params)]))
                    (debug [message params]
                      (swap! calls conj [:debug message (vec params)]))
                    (info [message params]
                      (swap! calls conj [:info message (vec params)]))
                    (warn [message params]
                      (swap! calls conj [:warn message (vec params)]))
                    (error [message params]
                      (swap! calls conj [:error message (vec params)]))
                    (fatal [message params]
                      (swap! calls conj [:fatal message (vec params)]))
                    (log [& args]
                      (case (count args)
                        3 (let [[level message params] args]
                            (swap! calls conj [:log-basic level message (vec params)]))
                        4 (let [[level date-or-params message params] args]
                            (if (instance? SentryDate date-or-params)
                              (swap! calls conj [:log-with-date level date-or-params message (vec params)])
                              (swap! calls conj [:log-with-params level date-or-params message (vec params)])))))))
     :calls calls}))

(defexpect level-specific-function-mock-test
  (setup-test-sentry!)
  (let [{:keys [calls logger-fn]} (mock-logger)]
    (with-redefs [sut/get-sentry-logger logger-fn]
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

(defexpect log-function-mock-test
  (setup-test-sentry!)
  (let [{:keys [calls logger-fn]} (mock-logger)]
    (with-redefs [sut/get-sentry-logger logger-fn]
      (expecting "basic logging with message only"
        (reset! calls [])
        (sut/log :info "test message")
        (expect 1 (count @calls))
        (let [[method-name level message params] (first @calls)]
          (expect :log-basic method-name)
          (expect SentryLogLevel/INFO level)
          (expect "test message" message)
          (expect [] params)))

      (expecting "basic logging with formatted message"
        (reset! calls [])
        (sut/log :error "error: %s %d" "arg1" 42)
        (expect 1 (count @calls))
        (let [[method-name level message params] (first @calls)]
          (expect :log-basic method-name)
          (expect SentryLogLevel/ERROR level)
          (expect "error: %s %d" message)
          (expect ["arg1" 42] params)))

      (expecting "structured logging with map"
        (reset! calls [])
        (sut/log :warn {:user-id "123" :operation "test"} "warning message")
        (expect 1 (count @calls))
        (let [[method-name level params-obj message args] (first @calls)]
          (expect :log-with-params method-name)
          (expect SentryLogLevel/WARN level)
          (expect true (instance? SentryLogParameters params-obj))
          (expect "warning message" message)
          (expect [] args)))

      (expecting "structured logging with map and format args"
        (reset! calls [])
        (sut/log :fatal {:critical true} "critical error: %s" "database down")
        (expect 1 (count @calls))
        (let [[method-name level params-obj message args] (first @calls)]
          (expect :log-with-params method-name)
          (expect SentryLogLevel/FATAL level)
          (expect true (instance? SentryLogParameters params-obj))
          (expect "critical error: %s" message)
          (expect ["database down"] args)))

      (expecting "logging with SentryDate"
        (reset! calls [])
        (let [test-date (SentryInstantDate.)]
          (sut/log :debug test-date "debug with date")
          (expect 1 (count @calls))
          (let [[method-name level date message args] (first @calls)]
            (expect :log-with-date method-name)
            (expect SentryLogLevel/DEBUG level)
            (expect test-date date)
            (expect "debug with date" message)
            (expect [] args))))

      (expecting "logging with SentryDate and format args"
        (reset! calls [])
        (let [test-date (SentryInstantDate.)]
          (sut/log :trace test-date "trace: %s %d" "value" 100)
          (expect 1 (count @calls))
          (let [[method-name level date message args] (first @calls)]
            (expect :log-with-date method-name)
            (expect SentryLogLevel/TRACE level)
            (expect test-date date)
            (expect "trace: %s %d" message)
            (expect ["value" 100] args))))

      (expecting "logging with SentryLogParameters"
        (reset! calls [])
        (let [test-params (SentryLogParameters/create (SentryAttributes/of (into-array SentryAttribute [])))]
          (sut/log :info test-params "info with params")
          (expect 1 (count @calls))
          (let [[method-name level params message args] (first @calls)]
            (expect :log-with-params method-name)
            (expect SentryLogLevel/INFO level)
            (expect test-params params)
            (expect "info with params" message)
            (expect [] args))))

      (expecting "logging with SentryLogParameters and format args"
        (reset! calls [])
        (let [test-params (SentryLogParameters/create (SentryAttributes/of (into-array SentryAttribute [])))]
          (sut/log :error test-params "error: %s" "failed")
          (expect 1 (count @calls))
          (let [[method-name level params message args] (first @calls)]
            (expect :log-with-params method-name)
            (expect SentryLogLevel/ERROR level)
            (expect test-params params)
            (expect "error: %s" message)
            (expect ["failed"] args))))

      (expecting "throws IllegalArgumentException for invalid arguments"
        (expect IllegalArgumentException (sut/log :info))
        (expect IllegalArgumentException (sut/log :info nil))
        (expect IllegalArgumentException (sut/log :info 123))
        (expect IllegalArgumentException (sut/log :info {:key "value"}))
        (expect IllegalArgumentException (sut/log :info (SentryInstantDate.)))))))
