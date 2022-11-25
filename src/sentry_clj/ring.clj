(ns sentry-clj.ring
  "Ring utility functions."
  (:require
   [clojure.string :refer [upper-case]]
   [ring.util.request :refer [request-url]]
   [ring.util.response :as response]
   [sentry-clj.core :as sentry]
   [sentry-clj.tracing :as st])
  (:import
   [io.sentry EventProcessor Hint IHub Sentry SentryEvent SentryTraceHeader]
   [io.sentry.protocol Request SentryTransaction]))

(set! *warn-on-reflection* true)

(def ^:private sentry-trace-header
  SentryTraceHeader/SENTRY_TRACE_HEADER)

(defn ^:private request->http
  "Converts a Ring request into an HTTP interface for an event."
  [req]
  {:url (request-url req)
   :method (-> req :request-method name)
   :data (:params req)
   :query-string (:query-string req "")
   :headers (:headers req)
   :env {:session (-> req :session pr-str)
         "REMOTE_ADDR" (:remote-addr req)}})

(defn ^:private get-current-hub
  "Get current Hub."
  []
  (Sentry/getCurrentHub))

(defn ^:private configure-scope!
  "Set scope a callback function which is called
   before a transaction finish or an event is send to Sentry."
  [^IHub hub scope-cb]
  (.configureScope hub (reify io.sentry.ScopeCallback
                         (run
                           [_ scope]
                           (scope-cb scope)))))

(defn ^:private request->user
  "Converts a Ring request into a User interface for an event."
  [req]
  {:ip-address (:remote-addr req)})

(defn ^:private request->event
  "Given a request and an exception, returns a Sentry event."
  [req e]
  {:throwable e
   :request (request->http req)
   :user (request->user req)})

(defn ^:private default-error
  "A very bare-bones error message. Ignores the request and exception."
  [_ _]
  (-> (str "<html><head><title>Error</title></head>"
           "<body><p>Internal Server Error</p></body></html>")
      (response/response)
      (response/content-type "text/html")
      (response/status 500)))

(defn ^:private extract-transaction-name
  "Extract transactin name from request.
   ex) GET /api/status"
  [{:keys [request-method uri]}]
  (str (-> request-method name upper-case) " " uri))

(defn ^:private request->context-request
  "Converts a request into custom-sampling-context's request."
  [req]
  {:uri (request-url req)
   :query-string (:query-string req "")
   :method (-> req :request-method name upper-case)
   :headers (:headers req)
   :data (-> req :params)})

(defn ^:private compute-sentry-runtime
  "Compute Clojure runtime information."
  []
  (let [runtime (io.sentry.protocol.SentryRuntime.)]
    (.setName runtime "Clojure")
    (.setVersion runtime (clojure-version))
    runtime))

(defn ^:private map->request
  "Converts a map into a Request."
  [{:keys [uri request-method query-string params headers] :as req}]
  (let [request (Request.)]
    (when uri
      (.setUrl request (request-url req)))
    (when request-method
      (.setMethod request (-> request-method name upper-case)))
    (when query-string
      (.setQueryString request query-string))
    (when params
      (.setData request (sentry/java-util-hashmappify-vals params)))
    (when headers
      (.setHeaders request (sentry/java-util-hashmappify-vals headers)))
    request))

(defn ^:private event-processor
  "This process is executed before a transaction finish or an event is sent."
  []
  (reify EventProcessor
    (^SentryEvent process
      [_ ^SentryEvent event ^Hint _hint]
      (.setRuntime (.getContexts event) (compute-sentry-runtime))
      event)

    (^SentryTransaction process
      [_ ^SentryTransaction tran ^Hint _hint]
      (.setRuntime (.getContexts tran) (compute-sentry-runtime))
      tran)))

(defn wrap-report-exceptions
  "Wraps the given handler in error reporting.

   Optionally takes three functions:

   * `:preprocess-fn`, which is passed the request
   * `:postprocess-fn`, which is passed the request and the Sentry event
   * `:error-fn`, which is passed the request and the thrown exception and returns an appropriate Ring response
   "
  [handler {:keys [preprocess-fn postprocess-fn error-fn]
            :or   {preprocess-fn identity
                   postprocess-fn (comp second list)
                   error-fn default-error}}]
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

(defn wrap-sentry-tracing
  "Wraps the given handler in tracing.

  Optionally takes one function:

  * `:preprocess-fn`, which is passed the request"
  ([handler]
   (wrap-sentry-tracing handler {}))
  ([handler {:keys [preprocess-fn]
             :or   {preprocess-fn identity}}]
   (fn [req]
     (let [trace-id (get (:headers req) sentry-trace-header)
           name (extract-transaction-name req)
           custom-sampling-context (->> req
                                        request->context-request
                                        (st/compute-custom-sampling-context "request"))
           transaction (st/start-transaction name
                                             "http.server"
                                             custom-sampling-context
                                             trace-id)]
       (-> (get-current-hub)
           (configure-scope! (fn [scope]
                               (st/swap-scope-request! scope (map->request (preprocess-fn req)))
                               (st/add-event-processor scope (event-processor)))))

       (try
         (let [res (handler req)]
           (st/swap-transaction-status! transaction (:ok st/span-status))
           res)
         (catch Throwable e
           (st/swap-transaction-status! transaction (:internal-error st/span-status))
           (throw e))
         (finally
           (st/finish-transaction! transaction)))))))
