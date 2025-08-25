(ns basic.main
  (:require
   [clojure.tools.logging :as log]
   [sentry-clj.core :as sentry]))

(defn ^:private create-sentry-logger
  "Create a Sentry Logger using the supplied `dsn`.
   If no `dsn` is supplied, simply log the `event` to a `logger`."
  [{:keys [dsn] :as config}]
  (if dsn
    (do
      (log/infof "Initialising Sentry with '%s'." dsn)
      (sentry/init! dsn config)
      (fn [event]
        (try
          (sentry/send-event event)
          (catch Exception e
            (log/errorf "Error submitting event '%s' to Sentry!" event)
            (log/error e)))))
    (do
      (log/warn "No Sentry DSN provided. Sentry events will be logged locally!")
      (fn [event]
        (log/infof "Sentry Event '%s'." event)))))

(defn init-sentry
  "Initialise Sentry with the provided `config` and return a function that can be
   used in your application for logging of interesting events to Sentry, for example:

   ```clojure
   (def sentry-logger (init-sentry {:dsn \"https://abcdefg@sentry.io:9000/2\" :environment \"local\"}))

   (sentry-logger {:message \"Oh No!\" :throwable (RuntimeException. \"Something bad has happened!\")})
   ```

   It is **highly** recommended that a system such as [Juxt
   Clip](https://github.com/juxt/clip), or
   [Integrant](https://github.com/weavejester/integrant), or
   [Component](https://github.com/stuartsierra/component) or another
   lifecycle/dependency manager is used to create and maintain the
   `sentry-logger` to ensure that it is only initialised only once. That
   function reference can then be then used in whichever namespaces that are
   appropriate for your application.

   sentry-clj does not maintain a single stateful instance of Sentry, thus it
   is entirely possible that it can be re-initialised multiple times.  **This
   behaviour is not ideal nor supported**."
  [config]
  (create-sentry-logger config))
