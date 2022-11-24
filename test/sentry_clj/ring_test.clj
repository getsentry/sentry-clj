(ns sentry-clj.ring-test
  (:require
   [cheshire.core :as json]
   [expectations.clojure.test :refer [defexpect expect expecting side-effects]]
   [sentry-clj.core :as sentry]
   [sentry-clj.ring :as ring]
   [sentry-clj.tracing :as st])
  (:import
   [io.sentry Hub JsonSerializer ScopeCallback Sentry SentryOptions]
   [java.io StringWriter]))

(defn serialize
  [event]
  (let [serializer (JsonSerializer. (SentryOptions.))
        sentry-event (#'sentry/map->event event)
        string-writer (StringWriter.)]
    (.serialize serializer sentry-event string-writer)
    string-writer))

(defn strip-timestamp
  [output]
  (let [result (-> (json/parse-string (str output))
                   (assoc-in ["sdk" "version"] "blah")
                   (dissoc "event_id" "timestamp"))]
    (assoc result "breadcrumbs" (map #(dissoc % "timestamp") (get result "breadcrumbs")))))

(def e (Exception. "thing"))

(defn wrapped
  [req]
  (if (:ok req) "woo" (throw e)))

(def req
  {:scheme :https
   :uri "/hello-world"
   :request-method :get
   :params {:one 1}
   :headers {"ok" 2 "host" "example.com"}
   :remote-addr "127.0.0.1"
   :session {}})

(defn preprocess
  [req]
  (update req :params assoc :two 2))

(defn postprocess
  [_ e]
  (assoc e :environment "qa"))

(defn error
  [req e]
  (assoc req :exception e))

(defexpect wrap-report-exceptions-test
  (expecting
   "passing through"
   (let [handler (ring/wrap-report-exceptions wrapped {})]
     (expect "woo" (handler (assoc req :ok true))))))

(defexpect with-defaults-test
  (expecting
   "with defaults"
   (let [event {:throwable e
                :request {:url "https://example.com/hello-world"
                          :method nil
                          :data {:one 1}
                          :query-string ""
                          :headers {"ok" 2 "host" "example.com"}
                          :env {:session "{}" "REMOTE_ADDR" "127.0.0.1"}}
                :user {:ip_address "127.0.0.1"}}
         sentry-event (strip-timestamp (serialize event))
         handler (ring/wrap-report-exceptions wrapped {})]
     (expect {:status 500
              :headers {"Content-Type" "text/html"}
              :body "<html><head><title>Error</title></head><body><p>Internal Server Error</p></body></html>"}
             (handler req))
     (expect {"breadcrumbs" (),
              "contexts" {},
              "request" {"data" {"one" 1},
                         "env" {"REMOTE_ADDR" "127.0.0.1", "session" "{}"},
                         "headers" {"host" "example.com", "ok" 2},
                         "query_string" "",
                         "url" "https://example.com/hello-world"},
              "sdk" {"version" "blah"},
              "user" {}} sentry-event))))

(defexpect with-callbacks-test
  (expecting
   "with callbacks"
   (let [event {:throwable e
                :environment "qa"
                :request {:url "https://example.com/hello-world"
                          :method nil
                          :data {:one 1 :two 2}
                          :query-string "?foo=bar"
                          :headers {"ok" 2 "host" "example.com"}
                          :env {:session "{}" "REMOTE_ADDR" "127.0.0.1"}}
                :user {:ip_address "127.0.0.1"}}
         sentry-event (strip-timestamp (serialize event))
         handler (ring/wrap-report-exceptions wrapped {:preprocess-fn  preprocess
                                                       :postprocess-fn postprocess
                                                       :error-fn       error})]
     (expect (assoc req :exception e) (handler req))
     (expect {"breadcrumbs" (),
              "contexts" {},
              "environment" "qa",
              "request" {"data" {"one" 1, "two" 2},
                         "env" {"session" "{}", "REMOTE_ADDR" "127.0.0.1"},
                         "headers" {"host" "example.com", "ok" 2},
                         "query_string" "?foo=bar",
                         "url" "https://example.com/hello-world"},
              "sdk" {"version" "blah"},
              "user" {}} sentry-event))))

(defn- get-test-options
  ([] (get-test-options {}))
  ([{:keys [traces-sample-rate]}]
   (let [sentry-option (SentryOptions.)]
     (.setDsn sentry-option "https://key@sentry.io/proj")
     (.setEnvironment sentry-option "development")
     (.setRelease sentry-option "release@1.0.0")
     (when traces-sample-rate
       (.setTracesSampleRate sentry-option traces-sample-rate))
     sentry-option)))

(defexpect wrap-sentry-tracing-test
  (let [sentry-option (get-test-options {:traces-sample-rate 1.0 :debug true})
        hub (Hub. sentry-option)
        ok-req (assoc req :ok true)]
    (Sentry/setCurrentHub hub)
    (expecting
      "passing through"
      (let [handler (ring/wrap-sentry-tracing wrapped)]
        (expect "woo" (handler ok-req))))
    (expecting
      "preprocess fn called"
      (let [handler (ring/wrap-sentry-tracing wrapped {:preprocess-fn preprocess})]
        (expect (fn [_transaction]
                  (= {"one" 1 "two" 2}
                     (let [p (promise)]
                       (.withScope hub (reify ScopeCallback
                                         (run [_ scope]
                                           (deliver p (.getData (.getRequest scope))))))
                       @p)))
          (side-effects [st/finish-transaction!]
            (expect "woo" (handler ok-req))))))))
