(ns sentry-clj.tracing-test
  (:require
   [expectations.clojure.test :refer [defexpect expect expecting]]
   [sentry-clj.tracing :as sut])
  (:import
   [io.sentry
    CustomSamplingContext
    Hub
    Scope
    Sentry
    SentryOptions
    SentryTracer
    Span
    SpanStatus
    TransactionContext]
   [io.sentry.protocol
    Request
    SentryId]))

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

(defn- ^SentryTracer get-test-sentry-tracer
  []
  (let [sentry-option (get-test-options)
        hub (Hub. sentry-option)
        tr (SentryTracer. (TransactionContext. "name" "op" true) hub)]
    (Sentry/setCurrentHub hub)
    (.configureScope hub (reify io.sentry.ScopeCallback
                           (run
                             [_ scope]
                             (.setTransaction scope tr))))
    tr))

(defexpect span-status-test
  (expecting
   "extract span status by keyword"
   (expect SpanStatus/OK (:ok sut/span-status))
   (expect SpanStatus/CANCELLED (:cancel sut/span-status))
   (expect SpanStatus/INTERNAL_ERROR (:internal-error sut/span-status))
   (expect SpanStatus/UNKNOWN (:unknown sut/span-status))
   (expect SpanStatus/UNKNOWN_ERROR (:unknown-error sut/span-status))
   (expect SpanStatus/INVALID_ARGUMENT (:invalid-argument sut/span-status))
   (expect SpanStatus/NOT_FOUND (:not-found sut/span-status))
   (expect SpanStatus/ALREADY_EXISTS (:already-exists sut/span-status))
   (expect SpanStatus/PERMISSION_DENIED (:permisson-denied sut/span-status))
   (expect SpanStatus/RESOURCE_EXHAUSTED (:resource-exhaused sut/span-status))
   (expect SpanStatus/FAILED_PRECONDITION (:fail-precondition sut/span-status))
   (expect SpanStatus/ABORTED (:aborted sut/span-status))
   (expect SpanStatus/OUT_OF_RANGE (:out-of-range sut/span-status))
   (expect SpanStatus/UNIMPLEMENTED (:unimplemented sut/span-status))
   (expect SpanStatus/DATA_LOSS (:data-loss sut/span-status))
   (expect SpanStatus/UNAUTHENTICATED (:unauthenticated sut/span-status))))

(defexpect compute-custom-sampling-context-test
  (expecting
   "compute custom-sampling-context"
   (let [request {"url" "http://example.com"
                  "method" "GET"
                  "headers" {"X-Clacks-Overhead" "Terry Pratchett"
                             "X-w00t" "ftw!"}
                  "data" "data"}
         csc (sut/compute-custom-sampling-context "request" request)]
     (expect (.get csc "request") request))))

(defexpect swap-transaction-status-test
  (expecting
   "change transaction status"
   (let [tr (get-test-sentry-tracer)]
     (sut/swap-transaction-status! tr (:ok sut/span-status))
     (expect (.getStatus tr) (:ok sut/span-status)))))

(defexpect finish-transaction!-test
  (expecting
   "transaction is finished"
   (let [tr (get-test-sentry-tracer)]
     (sut/finish-transaction! tr)
     (expect (.isFinished tr) true))))

(defexpect swap-scope-request!
  (expecting
   "set scope Request information"
   (let [option (get-test-options)
         scope (Scope. option)
         request (Request.)
         url "http://example.com"
         method "GET"
         query-string "?foo=bar"
         data {"baz" 1}]
     (.setUrl request url)
     (.setMethod request method)
     (.setQueryString request query-string)
     (.setData request data)
     (sut/swap-scope-request! scope request)

     (expect (-> (.getRequest scope) .getUrl) url)
     (expect (-> (.getRequest scope) .getMethod) method)
     (expect (-> (.getRequest scope) .getQueryString) query-string)
     (expect (-> (.getRequest scope) .getData) data))))

(defexpect with-start-child-span-test
  (expecting
   "when a child span is started and works correctly, span status is OK"
   (let [tr (get-test-sentry-tracer)]
     (sut/with-start-child-span "op" "desc" (println "hi"))
     (expect (.getStatus ^Span (nth (.getChildren tr) 0)) (:ok sut/span-status))
     (sut/finish-transaction! tr)))
  (expecting
   "when a child span is started and throw exceptions, span status is INTERNAL_ERROR"
   (let [tr (get-test-sentry-tracer)]
     (try
      (sut/with-start-child-span "op" "desc" (throw (ex-info "something-error" {})))
      (catch Throwable _)
      (finally
       (expect (.getStatus ^Span (nth (.getChildren tr) 0)) (:internal-error sut/span-status))
       (sut/finish-transaction! tr))))))

(defexpect start-transaction-test
  (expecting
   "when trace option isn't set, trace transaction isn't created"
   (let [sentry-option (get-test-options)
         hub (Hub. sentry-option)]
     (Sentry/setCurrentHub hub)
     (let [tr (sut/start-transaction "op" "http.server" (CustomSamplingContext.) nil)]
       (expect io.sentry.NoOpTransaction tr)
       (expect nil (sut/finish-transaction! tr)))))
  (expecting
   "when there is not a sentry-trace-header, new trace transaction is created"
   (let [sentry-option (get-test-options {:traces-sample-rate 1.0})
         hub (Hub. sentry-option)
         operation "opration"
         description "http.server"]
     (Sentry/setCurrentHub hub)
     (let [^SentryTracer tr (sut/start-transaction operation description (CustomSamplingContext.) nil)]
       (expect (.getName  tr) operation)
       (expect (.getOperation  tr) description)
       (expect (complement nil?) (.getTraceId (.toSentryTrace  tr)))
       (expect nil (sut/finish-transaction! tr)))))
  (expecting
   "when there is a sentry-trace-header, exist trace transaction is gotten"
   (let [sentry-option (get-test-options {:traces-sample-rate 1.0})
         hub (Hub. sentry-option)
         sentry-trace-header "f084f508efca47e9a3313851d4d8b7a2"
         operation "opration"
         description "http.server"]
     (Sentry/setCurrentHub hub)
     (let [^SentryTracer tr (sut/start-transaction operation description (CustomSamplingContext.) (str sentry-trace-header "-" "c1"))]
       (expect (.getName  tr) operation)
       (expect (.getOperation  tr) description)
       (expect (SentryId. sentry-trace-header) (.getTraceId (.toSentryTrace  tr)))
       (expect nil (sut/finish-transaction! tr))))))
