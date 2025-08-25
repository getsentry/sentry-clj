(ns dev
  (:require
   [basic.main :as main]))

(defn fire-event
  []
  ;;
  ;; Replace "dsn" below with a your DSN.
  ;;
  (let [dsn "http://da98da654fbc48f901ef3ff7cb173e4e@localhost:9000/1"
        ;;
        ;; Initialise Sentry, returning a function that will later receive an event.
        ;;
        sentry-logger (main/init-sentry {:dsn dsn :environment "local"})]
    ;;
    ;; "Fire" an event.
    ;;
    ;; This logger may be used as part of your normal application flow, perhaps if something
    ;; interesting happens, you may want Sentry to log it out.
    ;;
    ;; Perhaps in a handler that handles (throw (ex-info ..... ))
    ;;
    (sentry-logger {:message "Oh No!" :throwable (RuntimeException. "Something bad has happened!")})))
