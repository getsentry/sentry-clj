(ns sentry-clj.profiling-test
  (:require
   [expectations.clojure.test :refer [defexpect expect expecting]]
   [sentry-clj.profiling :as sut])
  (:import
   [io.sentry Sentry SentryOptions]))

(defn- setup-test-sentry!
  []
  (let [opts (SentryOptions.)]
    (.setDsn opts "https://key@sentry.io/proj")
    (.setEnvironment opts "test")
    (Sentry/init ^SentryOptions opts)))

(defexpect start-profiler-smoke-test
  (setup-test-sentry!)
  (expecting "start-profiler! returns nil without error"
    (expect nil? (sut/start-profiler!))))

(defexpect stop-profiler-smoke-test
  (setup-test-sentry!)
  (expecting "stop-profiler! returns nil without error"
    (expect nil? (sut/stop-profiler!))))

(defexpect with-profiling-test
  (expecting "returns the body's value"
    (with-redefs [sut/start-profiler! (fn [] nil)
                  sut/stop-profiler! (fn [] nil)]
      (expect 42 (sut/with-profiling 42))))

  (expecting "calls stop-profiler! even when body throws"
    (let [stopped? (atom false)]
      (with-redefs [sut/start-profiler! (fn [] nil)
                    sut/stop-profiler! (fn [] (reset! stopped? true))]
        (expect Exception
                (sut/with-profiling (throw (Exception. "boom"))))
        (expect true @stopped?)))))
