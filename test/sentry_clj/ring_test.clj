(ns sentry-clj.ring-test
  (:require
   [expectations.clojure.test :refer [defexpect expect expecting]]
   [mocko.core :refer :all]
   [sentry-clj.core :as sentry]
   [sentry-clj.ring :refer :all]))

(def e (Exception. "thing"))

(defn wrapped
  [req]
  (if (:ok req)
    "woo"
    (throw e)))

(def req
  {:scheme      :https
   :uri         "/hello-world"
   :method      :get
   :params      {:one 1}
   :headers     {"ok" 2 "host" "example.com"}
   :remote-addr "127.0.0.1"
   :session     {}})

(defn preprocess
  [req]
  (update req :params assoc :two 2))

(defn postprocess
  [req e]
  (assoc e :environment "qa"))

(defn error
  [req e]
  (assoc req :exception e))

(defexpect wrap-report-exceptions-test
  (expecting
   "passing through"
   (let [handler (wrap-report-exceptions wrapped {})]
     (expect "woo" (handler (assoc req :ok true))))))

(defexpect with-defaults-test
  (expecting
   "with defaults"
   (with-mocks
     (let [event   {:throwable e
                    :interfaces
                    {:request {:url          "https://example.com/hello-world"
                               :method       nil
                               :data         {:one 1}
                               :query_string ""
                               :headers      {"ok" 2 "host" "example.com"}
                               :env          {:session "{}" "REMOTE_ADDR" "127.0.0.1"}}
                     :user    {:ip_address "127.0.0.1"}}}
           handler (wrap-report-exceptions wrapped {})]
       (mock! #'sentry/send-event {[event] nil})
       (expect {:status  500
                :headers {"Content-Type" "text/html"}
                :body    "<html><head><title>Error</title></head><body><p>Internal Server Error</p></body></html>"}
               (handler req))))))

(defexpect with-callbacks-test
  (expecting
   "with callbacks"
   (with-mocks
     (let [event   {:throwable   e
                    :environment "qa"
                    :interfaces
                    {:request {:url          "https://example.com/hello-world"
                               :method       nil
                               :data         {:one 1 :two 2}
                               :query_string ""
                               :headers      {"ok" 2 "host" "example.com"}
                               :env          {:session "{}" "REMOTE_ADDR" "127.0.0.1"}}
                     :user    {:ip_address "127.0.0.1"}}}
           handler (wrap-report-exceptions wrapped {:preprocess-fn  preprocess
                                                    :postprocess-fn postprocess
                                                    :error-fn       error})]
       (mock! #'sentry/send-event {[event] nil})
       (expect (assoc req :exception e) (handler req))))))
