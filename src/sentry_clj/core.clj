(ns sentry-clj.core
  "A thin wrapper around the official Java library for Sentry."
  (:require
   [clojure.string :refer [blank?]]
   [clojure.walk :as walk])
  (:import
   [io.sentry Breadcrumb DateUtils EventProcessor Sentry SentryEvent SentryLevel SentryOptions Instrumenter]
   [io.sentry.protocol Message Request SentryId User]
   [java.util Date HashMap Map UUID]))

(set! *warn-on-reflection* true)

(defn ^:private keyword->level
  "Converts a keyword into an event level."
  [level]
  (case level
    :debug SentryLevel/DEBUG
    :info SentryLevel/INFO
    :warning SentryLevel/WARNING
    :error SentryLevel/ERROR
    :fatal SentryLevel/FATAL
    SentryLevel/INFO))

(defn java-util-hashmappify-vals
  "Converts an ordinary Clojure map into a java.util.HashMap object.
   This is done recursively for all nested maps as well.
   Keywords in any nested values are converted to strings.
   Based on walk/stringify-keys."
  [m]
  (walk/postwalk (fn [x] (cond
                           (map? x) (HashMap. ^Map x)
                           (keyword? x) (str (symbol x))
                           :else x))
                 m))

(defn ^:private map->breadcrumb
  "Converts a map into a Breadcrumb."
  ^Breadcrumb
  [{:keys [type level message category data timestamp]}]
  (let [breadcrumb (if timestamp (Breadcrumb. ^Date timestamp) (Breadcrumb.))]
    (when type
      (.setType breadcrumb type))
    (when level
      (.setLevel breadcrumb (keyword->level level)))
    (when message
      (.setMessage breadcrumb message))
    (when category
      (.setCategory breadcrumb category))
    (when data
      (doseq [[k v] (java-util-hashmappify-vals data)]
        (.setData breadcrumb k v)))
    breadcrumb))

(defn ^:private map->user
  "Converts a map into a User."
  ^User
  [{:keys [data email id ip-address username]}]
  (let [user (User.)]
    (when data
      (.setData user data))
    (when email
      (.setEmail user email))
    (when id
      (.setId user id))
    (when ip-address
      (.setIpAddress user ip-address))
    (when username
      (.setUsername user username))
    user))

(defn ^:private map->request
  "Converts a map into a Request."
  ^Request
  [{:keys [data env headers method other query-string url] :as _request}]
  (let [request (Request.)]
    (when data
      (.setData request (java-util-hashmappify-vals data)))
    (when env
      (.setEnvs request (java-util-hashmappify-vals env)))
    (when headers
      (.setHeaders request (java-util-hashmappify-vals headers))
      (when-let [cookie (get headers "cookie" (get headers "Cookie"))]
        (.setCookies request cookie)))
    (when method
      (.setMethod request method))
    (when other
      (.setOthers request (java-util-hashmappify-vals other)))
    (when query-string
      (.setQueryString request query-string))
    (when url
      (.setUrl request url))
    request))

(defn ^:private merge-all-ex-data
  "Merges ex-data of all ex-info exceptions in the cause chain of exn into extra.
   Each ex-data is added under a separate key so that they don't clobber each other."
  [extra exn]
  (loop [exn exn
         num 0
         extra extra]
    (if exn
      (let [data (ex-data exn)]
        (recur (ex-cause exn)
               (inc num)
               (cond-> extra
                 data (assoc (if (zero? num) "ex-data" (str "ex-data, cause " num ": " (ex-message exn))) data))))
      extra)))

(defn ^:private map->event
  "Converts a map into an event."
  ^SentryEvent
  [{:keys [event-id message level release environment user request logger platform dist tags breadcrumbs server-name extra fingerprints throwable transaction] :as _event}]
  (let [sentry-event (SentryEvent. (DateUtils/getCurrentDateTime))
        updated-message (if (string? message) {:message message} message)]
    (when event-id
      (.setEventId sentry-event (SentryId. ^UUID event-id)))
    (when-let [{:keys [formatted message params]} updated-message]
      (.setMessage sentry-event (doto (Message.) (.setFormatted formatted) (.setMessage message) (.setParams params))))
    (when level
      (.setLevel sentry-event (keyword->level level)))
    (when dist
      (.setDist sentry-event dist))
    (when release
      (.setRelease sentry-event release))
    (when environment
      (.setEnvironment sentry-event environment))
    (when user
      (.setUser sentry-event (map->user user)))
    (when request
      (.setRequest sentry-event (map->request request)))
    (when logger
      (.setLogger sentry-event logger))
    (when platform
      (.setPlatform sentry-event platform))
    (when transaction
      (.setTransaction sentry-event transaction))
    (doseq [[k v] tags]
      (.setTag sentry-event (name k) (str v)))
    (when (seq breadcrumbs)
      (doseq [breadcrumb (mapv map->breadcrumb breadcrumbs)]
        (.addBreadcrumb sentry-event ^Breadcrumb breadcrumb)))
    (when server-name
      (.setServerName sentry-event server-name))
    (when-let [data (merge-all-ex-data extra throwable)]
      (doseq [[k v] (java-util-hashmappify-vals data)]
        (.setExtra sentry-event k v)))
    (when throwable
      (.setThrowable sentry-event throwable))
    (when (seq fingerprints)
      (.setFingerprints sentry-event fingerprints))
    sentry-event))

(def ^:private sentry-defaults
  {:environment "production"
   :debug false ;; Java SDK default
   :enable-uncaught-exception-handler true ;; Java SDK default
   :trace-options-requests true ;; Java SDK default
   :serialization-max-depth 5 ;; default to 5, adjust lower if a circular reference loop occurs.
   :enabled true})

(defn ^:private sentry-options
  ^SentryOptions
  ([dsn] (sentry-options dsn {}))
  ([dsn config]
   (let [{:keys [enable-external-configuration
                 environment
                 debug
                 logger
                 diagnostic-level
                 release
                 dist
                 server-name
                 shutdown-timeout-millis
                 in-app-includes
                 in-app-excludes
                 ignored-exceptions-for-type
                 enable-uncaught-exception-handler
                 before-send-fn
                 before-breadcrumb-fn
                 serialization-max-depth
                 traces-sample-rate
                 traces-sample-fn
                 trace-options-requests
                 instrumenter
                 event-processors
                 enabled]} (merge sentry-defaults config)
         sentry-options (SentryOptions.)]

     (.setDsn sentry-options dsn)

     (when enable-external-configuration
       (.setEnableExternalConfiguration sentry-options enable-external-configuration))
     (when environment
       (.setEnvironment sentry-options environment))
     ;;
     ;; When serializing out an object, say a Throwable, sometimes it happens
     ;; that the serialization goes into a circular reference loop and just locks up
     ;;
     ;; Turning on `{:debug true}` when initializing Sentry should expose the issue on your logs
     ;;
     ;; If you experience this issue, try adjusting the maximum depth to a low
     ;; number, such as 2 and see if that works for you.
     ;;
     (when serialization-max-depth
       (.setMaxDepth sentry-options serialization-max-depth)) ;; defaults to 100 in the SDK, but we default it to 5.
     (when release
       (.setRelease sentry-options release))
     (when dist
       (.setDist sentry-options dist))
     (when server-name
       (.setServerName sentry-options ^String server-name))
     (when shutdown-timeout-millis
       (.setShutdownTimeoutMillis sentry-options shutdown-timeout-millis)) ;; already set to 2000ms in the SDK
     (doseq [in-app-include in-app-includes]
       (.addInAppInclude sentry-options in-app-include))
     (doseq [in-app-exclude in-app-excludes]
       (.addInAppExclude sentry-options in-app-exclude))
     (doseq [ignored-exception-for-type ignored-exceptions-for-type]
       (try
         (let [clazz (Class/forName ignored-exception-for-type)]
           (when (isa? clazz Throwable)
             (.addIgnoredExceptionForType sentry-options ^Throwable clazz)))
         (catch Exception _))) ; just ignore it.
     (when before-send-fn
       (.setBeforeSend sentry-options ^SentryEvent
                       (reify io.sentry.SentryOptions$BeforeSendCallback
                         (execute [_ event hint]
                           (before-send-fn event hint)))))
     (when before-breadcrumb-fn
       (.setBeforeBreadcrumb sentry-options ^Breadcrumb
                             (reify io.sentry.SentryOptions$BeforeBreadcrumbCallback
                               (execute [_ breadcrumb hint]
                                 (before-breadcrumb-fn breadcrumb hint)))))
     (when traces-sample-rate
       (.setTracesSampleRate sentry-options traces-sample-rate))
     (when traces-sample-fn
       (.setTracesSampler sentry-options ^io.sentry.SentryOptions$TracesSamplerCallback
                          (reify io.sentry.SentryOptions$TracesSamplerCallback
                            (sample [_ ctx]
                              (traces-sample-fn {:custom-sample-context (-> ctx
                                                                            .getCustomSamplingContext
                                                                            .getData)
                                                 :transaction-context (.getTransactionContext ctx)})))))
     (when-let [instrumenter (case instrumenter
                               :sentry Instrumenter/SENTRY
                               :otel Instrumenter/OTEL
                               nil)]
       (.setInstrumenter sentry-options ^Instrumenter instrumenter))

     (doseq [event-processor event-processors]
       (.addEventProcessor sentry-options ^EventProcessor event-processor))

     (.setDebug sentry-options debug)
     (.setLogger sentry-options logger)
     (.setDiagnosticLevel sentry-options (keyword->level diagnostic-level))
     (.setTraceOptionsRequests sentry-options trace-options-requests)
     (.setEnableUncaughtExceptionHandler sentry-options enable-uncaught-exception-handler)
     (.setEnabled sentry-options enabled)

     sentry-options)))

(defn init!
  "Initialize Sentry with the mandatory `dsn`

   Other options include:

   | key                                  | description                                                                                                        | default
   | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------ | -------
   | `:enable-external-configuration`     | Enable loading configuration from the properties file, system properties or environment variables                  |
   | `:environment`                       | Set the environment on which Sentry events will be logged, e.g., \"production\"                                    | production
   | `:enabled`                           | Enable or disable sentry.                                                                                          | true
   | `:debug`                             | Enable SDK logging at the debug level                                                                              | false
   | `:logger`                            | Instance of `io.sentry.ILogger` (only applies when `:debug` is on)                                                 | `io.sentry.SystemOutLogger`
   | `:diagnostic-level`                  | Log messages at or above this level (only applies when `:debug` is on)                                             | `:debug`
   | `:release`                           | All events are assigned to a particular release                                                                    |
   | `:dist`                              | Set the application distribution that will be sent with each event                                                 |
   | `:server-name`                       | Set the server name that will be sent with each event                                                              |
   | `:shutdown-timeout-millis`           | Wait up to X milliseconds before shutdown if there are events to send                                              | 2000ms
   | `:in-app-includes`                   | A seqable collection (vector for example) containing package names to include when sending events                  |
   | `:in-app-excludes`                   | A seqable collection (vector for example) containing package names to ignore when sending events                   |
   | `:ignored-exceptions-for-type        | Set exceptions that will be filtered out before sending to Sentry (a set of Classnames as Strings)                 |
   | `:enable-uncaught-exception-handler` | Enables the uncaught exception handler                                                                             | true
   | `:before-send-fn`                    | A function (taking an event and a hint)                                                                            |
   |                                      | The body of the function must not be lazy (i.e., don't use filter on its own!) and must return an event or nil     |
   |                                      | If a nil is returned, the event will not be sent to Sentry                                                         |
   |                                      | [More Information](https://docs.sentry.io/platforms/java/data-management/sensitive-data/)                          |
   | `:before-breadcrumb-fn`              | A function (taking a breadcrumb and a hint)                                                                        |
   |                                      | The body of the function must not be lazy (i.e., don't use filter on its own!) and must return a breadcrumb or nil |
   |                                      | If a nil is returned, the breadcrumb will not be sent to Sentry                                                    |
   |                                      | [More Information](https://docs.sentry.io/platforms/java/enriching-events/breadcrumbs/)                            |
   | `:contexts`                          | A map of key/value pairs to attach to every Event that is sent.                                                    |
   |                                      | [More Information)(https://docs.sentry.io/platforms/java/enriching-events/context/)                                |
   | `:traces-sample-rate`                | Set a uniform sample rate(a number of between 0.0 and 1.0) for all transactions for tracing                        |
   | `:traces-sample-fn`                  | A function (taking a custom sample context and a transaction context) enables you to control trace transactions    |
   | `:serialization-max-depth`           | Set to a lower number, i.e., 2, if you experience circular reference errors when sending events                    | 5
   | `:trace-options-request`             | Set to enable or disable tracing of options requests                                                               | true

   Some examples:

   ```clojure
   (init! \"http://abcdefg@localhost:19000/2\")
   ```

   ```clojure
   (init! \"http://abcdefg@localhost:19000/2\" {:environment \"production\" :debug true :release \"foo.bar@1.0.0\" :in-app-excludes [\"foo.bar\"])
   ```

   ```clojure
   (init! \"http://abcdefg@localhost:19000/2\" {:before-send-fn (fn [event _] (when-not (= (.. event getMessage getMessage \"foo\")) event))})
   ```

   ```clojure
   (init! \"http://abcdefg@localhost:19000/2\" {:before-send-fn (fn [event _] (.setServerName event \"fred\") event)})
   ```

   ```clojure
   (init! \"http://abcdefg@localhost:19000/2\" {:contexts {:foo \"bar\" :baz \"wibble\"}})
   ```
   "
  ([dsn] (init! dsn {}))
  ([dsn {:keys [contexts] :as config}]
   {:pre [(not (blank? dsn))]}
   (let [options (sentry-options dsn config)]
     (Sentry/init ^SentryOptions options)
     (when contexts
       (Sentry/configureScope (reify io.sentry.ScopeCallback
                                (run [_ scope]
                                  (doseq [[k v] (java-util-hashmappify-vals contexts)]
                                    (.setContexts scope ^String k ^Object {"value" v})))))))))

(defn close!
  "Closes the SDK"
  []
  (Sentry/close))

(defn flush-events
  "Flushes events to Sentry, blocking until the flush is complete.

   You can pass a timeout in milliseconds to wait for the flush to complete."
  [timeout-millis]
  (Sentry/flush timeout-millis))

(defn send-event
  "Sends the given event to Sentry, returning the event's id

   Supports sending throwables:

   ```
   (sentry/send-event {:message   \"oh no\",
   :throwable (RuntimeException. \"foo bar\"})
   ```
   "
  [event]
  (str (Sentry/captureEvent (map->event event))))
