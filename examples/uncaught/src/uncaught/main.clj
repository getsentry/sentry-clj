(ns uncaught.main
  (:require
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.tools.logging :as log]
   [sentry-clj.core :as sentry])
  (:gen-class))

(defn ^:private set-default-exception-handler
  "Register our own Uncaught Exception Handler using the provided `sentry-client`."
  [sentry-logger]
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ _thread ex]
       (log/warn ex "Uncaught Exception!")
       (sentry-logger {:throwable ex})))))

(defn ^:private create-sentry-logger
  [{:keys [dsn environment] :as config}]
  (when dsn
    (log/infof "Initialising Sentry with '%s' and Environment '%s'." dsn environment)
    (sentry/init! dsn config)
    (fn [event]
      (try
        (sentry/send-event event)
        (catch Exception e
          (log/errorf "Error submitting event '%s' to Sentry!" event)
          (log/error e))))))

(def ^:private cli-options
  [["-d" "--dsn DSN" "DSN to use."]])

(defn ^:private validate-args
  [args]
  (let [{{:keys [dsn]} :options} (parse-opts args cli-options)]
    (cond
      dsn {:dsn dsn}
      :else {:failure "You must provide a DSN!"})))

(defn ^:private exit
  [message]
  (println message)
  (System/exit -1))

;; In this example, we are going to manage the uncaught exceptions ourselves, so that
;; not only do we have a chance to fire a Sentry event, but we may also wish to do other
;; things too! (This simple example only additionally logs out the error).

(defn -main
  [& args]
  (let [{:keys [dsn failure]} (validate-args args)]
    (if failure
      (exit failure)
      (do
        (set-default-exception-handler (create-sentry-logger {:dsn dsn :environment "production" :debug true :enable-uncaught-exception-handler false}))
        (log/info "Press `ctrl+c` to quit once the event has been sent by Sentry!")
        (doto
         (Thread. (fn [] (/ 42 0))) ;; Kaboom!
          .start)
        @(promise))))) ;; Spin waiting for the `ctrl+c` or quit to arrive...
