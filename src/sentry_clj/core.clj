(ns sentry-clj.core
  "A thin wrapper around the official Java library for Sentry."
  (:require
   [clojure.string :refer [blank?]]
   [clojure.walk :as walk])
  (:import
   [io.sentry Breadcrumb DateUtils Sentry SentryEvent SentryLevel SentryOptions]
   [io.sentry.protocol Message Request SentryId User]
   [java.util HashMap Map UUID Date]))

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
  "Converts an ordinary Clojure map into a Clojure map with nested map
  values recursively translated into java.util.HashMap objects. Based
  on walk/stringify-keys."
  [m]
  (let [f (fn [[k v]]
            (let [k (if (keyword? k) (name k) k)
                  v (if (keyword? v) (name v) v)]
              (if (map? v) [k (HashMap. ^Map v)] [k v])))]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn ^:private ^Breadcrumb map->breadcrumb
  "Converts a map into a Breadcrumb."
  [{:keys [type level message category data timestamp]}]
  (let [breadcrumb (if timestamp
                     (Breadcrumb. ^Date timestamp)
                     (Breadcrumb.))]
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

(defn ^:private ^User map->user
  "Converts a map into a User."
  [{:keys [email id username ip-address other]}]
  (let [user (User.)]
    (when email
      (.setEmail user email))
    (when id
      (.setId user id))
    (when username
      (.setUsername user username))
    (when ip-address
      (.setIpAddress user ip-address))
    (when other
      (.setOthers user other))
    user))

(defn ^:private ^Request map->request
  "Converts a map into a Request."
  [{:keys [url method query-string data cookies headers env other]}]
  (let [request (Request.)]
    (when url
      (.setUrl request url))
    (when method
      (.setMethod request method))
    (when query-string
      (.setQueryString request query-string))
    (when data
      (.setData request (java-util-hashmappify-vals data)))
    (when cookies
      (.setCookies request (java-util-hashmappify-vals cookies)))
    (when headers
      (.setHeaders request (java-util-hashmappify-vals headers)))
    (when env
      (.setEnvs request (java-util-hashmappify-vals env)))
    (when other
      (.setOthers request (java-util-hashmappify-vals other)))
    request))

(defn ^:private ^SentryEvent map->event
  "Converts a map into an event."
  [{:keys [event-id message level release environment user request logger platform dist
           tags breadcrumbs server-name extra fingerprints throwable transaction]}]
  (let [sentry-event (SentryEvent. (DateUtils/getCurrentDateTime))
        updated-message (if (string? message) {:message message} message)]
    (when event-id
      (.setEventId sentry-event (SentryId. ^UUID event-id)))
    (when-let [{:keys [formatted message params]} updated-message]
      (.setMessage sentry-event (doto
                                  (Message.)
                                  (.setFormatted formatted)
                                  (.setMessage message)
                                  (.setParams params))))
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
    (when-let [data (merge extra (ex-data throwable))]
      (doseq [[k v] (java-util-hashmappify-vals data)]
        (.setExtra sentry-event k v)))
    (when throwable
      (.setThrowable sentry-event throwable))
    (when (seq fingerprints)
      (.setFingerprints sentry-event fingerprints))
    sentry-event))

(def ^:private sentry-defaults
  {:debug false
   :environment "production"
   :enable-uncaught-exception-handler true
   :uncaught-handler-enabled true})

(defn ^:private ^SentryOptions sentry-options
  [dsn config]
  (let [{:keys [environment
                debug
                release
                dist
                server-name
                shutdown-timeout
                in-app-includes
                in-app-excludes
                ignored-exceptions-for-type
                enable-uncaught-exception-handler ;; deprecated
                uncaught-handler-enabled
                before-send-fn
                before-breadcrumb-fn
                traces-sample-rate
                traces-sample-fn]} (merge sentry-defaults config)
        sentry-options (SentryOptions.)]

    (.setDsn sentry-options dsn)

    (when environment
      (.setEnvironment sentry-options environment))
    (when debug
      (.setDebug sentry-options debug)) ;; already set to `false` in the SDK.
    (when release
      (.setRelease sentry-options release))
    (when dist
      (.setDist sentry-options dist))
    (when server-name
      (.setServerName sentry-options ^String server-name))
    (when shutdown-timeout
      (.setShutdownTimeout sentry-options shutdown-timeout)) ;; already set to 2000ms in the SDK
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
    (when-not (and enable-uncaught-exception-handler uncaught-handler-enabled)
      (.setEnableUncaughtExceptionHandler sentry-options false)) ;; already true in the SDK
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
      (.setTracesSampler sentry-options ^io.sentry.SentryOptions$TracesSamplerCallback (reify io.sentry.SentryOptions$TracesSamplerCallback
                                                                                         (sample
                                                                                             [_ ctx]
                                                                                           (traces-sample-fn {:custom-sample-context (-> ctx
                                                                                                                                         .getCustomSamplingContext
                                                                                                                                         .getData)
                                                                                                              :transaction-context (.getTransactionContext ctx)})))))
    sentry-options))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn init!
  "Initialize Sentry with the mandatory `dsn`

   Other options include:

   | key                                  | description                                                                                                        | default
   | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------ | -------
   | `:environment`                       | Set the environment on which Sentry events will be logged, e.g., \"production\"                                    | production
   | `:debug`                             | Enable SDK logging at the debug level                                                                              | false
   | `:release`                           | All events are assigned to a particular release                                                                    |
   | `:dist`                              | Set the application distribution that will be sent with each event                                                 |
   | `:server-name`                       | Set the server name that will be sent with each event                                                              |
   | `:shutdown-timeout`                  | Wait up to X milliseconds before shutdown if there are events to send                                              | 2000ms
   | `:in-app-includes`                   | A seqable collection (vector for example) containing package names to include when sending events                  |
   | `:in-app-excludes`                   | A seqable collection (vector for example) containing package names to ignore when sending events                   |
   | `:ignored-exceptions-for-type        | Set exceptions that will be filtered out before sending to Sentry (a set of Classnames as Strings)                 |
   | `:enable-uncaught-exception-handler` | (deprecated, use :uncaught-handler-enabled instead) Enables the uncaught exception handler                         | true
   | `:uncaught-handler-enabled`          | Enables the uncaught exception handler                                                                             | true
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
     (Sentry/init options)
     (when contexts
       (Sentry/configureScope (reify io.sentry.ScopeCallback
                                (run [_ scope]
                                  (doseq [[k v] (java-util-hashmappify-vals contexts)]
                                    (.setContexts scope ^String k ^Object {"value" v})))))))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn close!
  "Closes the SDK"
  []
  (Sentry/close))

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
