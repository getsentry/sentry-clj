(ns sentry-clj.logging
  (:import [io.sentry Sentry]))

; TODO: improve with other log levels and generic log function
(defn log!
  [message & args]
  (let [array-params (when (seq args)
                       (into-array Object args))]
    (.info (Sentry/logger) message array-params)))
