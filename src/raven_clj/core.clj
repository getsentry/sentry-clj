(ns raven-clj.core
  "A thin wrapper around the official Java library for Sentry."
  (:require [clj-time.coerce :as tc]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [raven-clj.internal :as internal])
  (:import (java.util HashMap UUID)
           (com.getsentry.raven Raven)
           (com.getsentry.raven.dsn Dsn)
           (com.getsentry.raven.event Breadcrumb$Level
                                      Breadcrumb$Type
                                      BreadcrumbBuilder
                                      Event
                                      Event$Level
                                      EventBuilder)
           (com.getsentry.raven.event.interfaces ExceptionInterface)))

(def ^:private instance
  "A function which returns a Raven instance given a DSN."
  (memoize (fn [^String dsn]
             (.createRavenInstance internal/factory (Dsn. dsn)))))

(defn- keyword->level
  "Converts a keyword into an event level."
  [level]
  (case level
    :debug   Event$Level/DEBUG
    :info    Event$Level/INFO
    :warning Event$Level/WARNING
    :error   Event$Level/ERROR
    :fatal   Event$Level/FATAL))

(defn- java-util-hashmappify-vals
  "Converts an ordinary Clojure map into a Clojure map with nested map
  values recursively translated into java.util.HashMap objects. Based
  on walk/stringify-keys."
  [m]
  (let [f (fn [[k v]]
            (let [k (if (keyword? k) (name k) k)]
              (if (map? v) [k (HashMap. v)] [k v])))]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn- map->breadcrumb
  "Converts a map into a breadcrumb."
  [{:keys [type timestamp level message category data]}]
  (let [b (BreadcrumbBuilder.)]
    (when type
      (.setType b (case type
                    :default    Breadcrumb$Type/DEFAULT
                    :http       Breadcrumb$Type/HTTP
                    :navigation Breadcrumb$Type/NAVIGATION)))
    (when timestamp
      (.setTimestamp b (tc/to-date timestamp)))
    (when level
      (.setLevel b (case level
                     :debug    Breadcrumb$Level/DEBUG
                     :info     Breadcrumb$Level/INFO
                     :warning  Breadcrumb$Level/WARNING
                     :error    Breadcrumb$Level/ERROR
                     :critical Breadcrumb$Level/CRITICAL)))
    (when message
      (.setMessage b message))
    (when category
      (.setCategory b category))
    (when data
      (.setData b data))
    (.build b)))

(defn- ^Event map->event
  "Converts a map into an event."
  [{:keys [event-id message level release environment logger platform culprit
           tags breadcrumbs server-name extra fingerprint checksum-for checksum
           interfaces throwable timestamp]}]
  (let [b (EventBuilder. (or event-id (UUID/randomUUID)))]
    (when message
      (.withMessage b message))
    (when level
      (.withLevel b (keyword->level level)))
    (when release
      (.withRelease b release))
    (when environment
      (.withEnvironment b environment))
    (when logger
      (.withLogger b logger))
    (when platform
      (.withPlatform b platform))
    (when culprit
      (.withCulprit b culprit))
    (doseq [[k v] tags]
      (.withTag b (name k) (str v)))
    (when (seq breadcrumbs)
      (.withBreadcrumbs b (mapv map->breadcrumb breadcrumbs)))
    (when server-name
      (.withServerName b server-name))
    (when-let [data (merge extra (ex-data throwable))]
      (doseq [[k v] (java-util-hashmappify-vals data)]
        (.withExtra b k v)))
    (when checksum-for
      (.withChecksumFor b checksum-for))
    (when checksum
      (.withChecksum b checksum))
    (doseq [[interface-name data] interfaces]
      (.withSentryInterface b (internal/->CljInterface (name interface-name)
                                                       data)))
    (when throwable
      (.withSentryInterface b (ExceptionInterface. ^Throwable throwable)))
    (when timestamp
      (.withTimestamp b (tc/to-date timestamp)))
    (when (seq fingerprint)
      (.withFingerprint b fingerprint))
    (.build b)))

(defn send-event
  "Sends the given event to Sentry, returning the event's ID.

  Supports sending throwables:

  ```
  (raven/send-event dsn {:message   \"oh no\",
                         :throwable e})
  ```

  Also supports interfaces with arbitrary values, e.g.:

  ```
  (raven/send-event dsn {:message    \"oh no\",
                         :interfaces {:user {:id    100
                                             :email \"test@example.com\"}}})
  ```
  "
  [dsn event]
  (let [e (map->event event)]
    (.sendEvent ^Raven (instance dsn) e)
    (-> e .getId (string/replace #"-" ""))))
