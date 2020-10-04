(ns sentry-clj.ring
  "Ring utility functions."
  (:require
   [ring.util.request :refer [request-url]]
   [ring.util.response :as response]
   [sentry-clj.core :as sentry]))

(defn- request->http
  "Converts a Ring request into an HTTP interface for an event."
  [req]
  {:url          (request-url req)
   :method       (:request-method req)
   :data         (:params req)
   :query_string (:query-string req "")
   :headers      (:headers req)
   :env          {:session      (-> req :session pr-str)
                  "REMOTE_ADDR" (:remote-addr req)}})

(defn- request->user
  "Converts a Ring request into a User interface for an event."
  [req]
  {:ip_address (:remote-addr req)})

(defn request->event
  "Given a request and an exception, returns a Sentry event."
  [req e]
  {:throwable  e
   :interfaces {:request (request->http req)
                :user    (request->user req)}})

(defn- default-error
  "A very bare-bones error message. Ignores the request and exception."
  [_ _]
  (-> (str "<html><head><title>Error</title></head>"
           "<body><p>Internal Server Error</p></body></html>")
      (response/response)
      (response/content-type "text/html")
      (response/status 500)))

(defn wrap-report-exceptions
  "Wraps the given handler in error reporting.

  Optionally takes three functions:

   * `:preprocess-fn`, which is passed the request
   * `:postprocess-fn`, which is passed the request and the Sentry event
   * `:error-fn`, which is passed the request and the thrown exception and
   returns an appropriate Ring response
   "
  [handler {:keys [preprocess-fn postprocess-fn error-fn]
            :or   {preprocess-fn  identity
                   postprocess-fn (comp second list)
                   error-fn       default-error}}]
  (fn [req]
    (try
     (handler req)
     (catch Throwable e
       (-> req
           preprocess-fn
           (request->event e)
           (->> (postprocess-fn req)
                sentry/send-event))
       (error-fn req e)))))
