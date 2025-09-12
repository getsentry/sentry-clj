(ns sentry-clj.logging
  "Structured logging integration with Sentry.
   
   Provides logging functions at all standard levels:
   trace, debug, info, warn, error, fatal"
  (:import [io.sentry Sentry]))

(defmacro deflogger
  "Defines a logging function for the given level."
  [level]
  `(defn ~level
     ~(str "Log a message at " level " level with optional format arguments.")
     [message# & args#]
     (let [array-params# (when (seq args#)
                          (into-array Object args#))]
       (~(symbol (str "." level)) (Sentry/logger) message# array-params#))))

; Generate all logging functions
(deflogger trace)
(deflogger debug) 
(deflogger info)
(deflogger warn)
(deflogger error)
(deflogger fatal)
