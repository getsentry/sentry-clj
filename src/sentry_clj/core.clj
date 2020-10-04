(ns sentry-clj.core
  "A thin wrapper around the official Java library for Sentry."
  (:require
   [clojure.walk :as walk])
  (:import
   [java.util HashMap Map List]
   [io.sentry Breadcrumb
    DateUtils
    Sentry
    SentryEvent
    SentryLevel
    SentryOptions]
   [io.sentry.protocol
    Message
    Request
    SentryId
    User]))

(defn- keyword->level
  "Converts a keyword into an event level."
  [level]
  (case level
    :debug   SentryLevel/DEBUG
    :info    SentryLevel/INFO
    :warning SentryLevel/WARNING
    :error   SentryLevel/ERROR
    :fatal   SentryLevel/FATAL
    SentryLevel/INFO))

(defn- java-util-hashmappify-vals
  "Converts an ordinary Clojure map into a Clojure map with nested map
  values recursively translated into java.util.HashMap objects. Based
  on walk/stringify-keys."
  [m]
  (let [f (fn [[k v]]
            (let [k (if (keyword? k) (name k) k)]
              (if (map? v) [k (HashMap. ^Map v)] [k v])))]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn- ^Breadcrumb map->breadcrumb
  "Converts a map into a Breadcrumb."
  [{:keys [type level message category data]}]
  (let [breadcrumb (Breadcrumb.)]
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

(defn- ^User map->user
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

(defn- ^Request map->request
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
      (.setData request data))
    (when cookies
      (.setCookies request cookies))
    (when headers
      (.setHeaders request headers))
    (when env
      (.setEnvs request env))
    (when other
      (.setOthers request other))
    request))

(defn- ^SentryEvent map->event
  "Converts a map into an event."
  [{:keys [event-id message level release environment user request logger platform dist
           tags breadcrumbs server-name extra fingerprints throwable transaction]}]
  (let [sentry-event (SentryEvent. (DateUtils/getCurrentDateTimeOrNull))]
    (when event-id
      (.setEventId sentry-event (SentryId. event-id)))
    (when message
      (let [msg (Message.)]
        (.setMessage msg message)
        (.setMessage sentry-event msg)))
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
        (.addBreadcrumb sentry-event breadcrumb)))
    (when server-name
      (.setServerName sentry-event server-name))
    (when-let [data (merge extra (ex-data throwable))]
      (doseq [[k v] (java-util-hashmappify-vals data)]
        (.setExtra sentry-event k v)))
    (when throwable
      (.setThrowable sentry-event ^Throwable throwable))
    (when (seq fingerprints)
      (.setFingerprints sentry-event ^List fingerprints))
    sentry-event))

(defn init!
  "Initialize Sentry with the mandatory `dsn`

   Other options include:

   | key                   | description |
   |-----------------------|-------------|
   | `:debug`              | Enable SDK logging at the debug level
   | `:release`            | All events are assigned to a particular release
   | `:shutdown-timeout`   | Wait up to X seconds before shutdown if there are events to send
   | `:in-app-excludes`    | a seqable collection (vector for example) containing package names to ignore when sending events

   For example:

   ```clojure
   (init! \"http://08b96b9fc63d4b8c8cb991a245ac129f@localhost:19000/2\")
   ```

   ```clojure
   (init! \"http://08b96b9fc63d4b8c8cb991a245ac129f@localhost:19000/2\" {:debug true :release \"foo.bar@1.0.0\" :shutdown-timeout 5000 :in-app-excludes [\"foo.bar\"])
   ```

   "
  ([dsn] (init! dsn {}))
  ([dsn {:keys [debug release shutdown-timeout in-app-excludes]}]
   (let [sentry-options (SentryOptions.)]
     (when debug
       (.setDebug sentry-options debug))
     (when release
       (.setRelease sentry-options release))
     (when shutdown-timeout
       (.setShutdownTimeout sentry-options shutdown-timeout))
     (doseq [in-app-exclude in-app-excludes]
       (.addInAppExclude sentry-options in-app-exclude))
     (.setDsn sentry-options dsn)
     (Sentry/init sentry-options))))

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
