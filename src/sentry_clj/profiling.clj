(ns sentry-clj.profiling
  "Wrapper for Sentry JVM profiling (manual lifecycle).

   For profiling to work, `io.sentry/sentry-async-profiler` must be on the classpath
   and Sentry must be initialized with `{:profile-session-sample-rate 1.0 :profile-lifecycle :manual}`."
  (:import
   [io.sentry Sentry]))

(set! *warn-on-reflection* true)

(defn start-profiler!
  "Starts the JVM profiler session.

   Only meaningful when `{:profile-lifecycle :manual}` is set in `sentry-clj.core/init!`
   and `io.sentry/sentry-async-profiler` is on the classpath."
  []
  (Sentry/startProfiler))

(defn stop-profiler!
  "Stops the current JVM profiler session and sends the profile to Sentry."
  []
  (Sentry/stopProfiler))

(defmacro with-profiling
  "Wraps `body` in a profiling session, calling `start-profiler!` before and
   `stop-profiler!` after. Guarantees `stop-profiler!` is called even if an
   exception is thrown."
  [& body]
  `(do
     (start-profiler!)
     (try
       ~@body
       (finally
         (stop-profiler!)))))
