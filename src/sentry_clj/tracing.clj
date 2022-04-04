(ns sentry-clj.tracing
  (:import
   [io.sentry
    CustomSamplingContext
    EventProcessor
    Scope
    Sentry
    ITransaction
    SpanStatus
    TransactionContext]))

(def span-status
  {:ok SpanStatus/OK
   :cancel SpanStatus/CANCELLED
   :internal-error SpanStatus/INTERNAL_ERROR
   :unknown SpanStatus/UNKNOWN
   :unknown-error SpanStatus/UNKNOWN_ERROR
   :invalid-argument SpanStatus/INVALID_ARGUMENT
   :deadline-exceeded SpanStatus/DEADLINE_EXCEEDED
   :not-found SpanStatus/NOT_FOUND
   :already-exists SpanStatus/ALREADY_EXISTS
   :permisson-denied SpanStatus/PERMISSION_DENIED
   :resource-exhaused SpanStatus/RESOURCE_EXHAUSTED
   :fail-precondition SpanStatus/FAILED_PRECONDITION
   :aborted SpanStatus/ABORTED
   :out-of-range SpanStatus/OUT_OF_RANGE
   :unimplemented SpanStatus/UNIMPLEMENTED
   :unavailable SpanStatus/UNAVAILABLE
   :data-loss SpanStatus/DATA_LOSS
   :unauthenticated SpanStatus/UNAUTHENTICATED})

(defn ^CustomSamplingContext compute-custom-sampling-context
  "Compute a custom sampling context has key and info."
  [key info]
  (let [csc (CustomSamplingContext.)]
    (.set csc key info)
    csc))

(defn start-transaction
  "Start tracing transactions.
   If a sentry-trace-header is given, connect the exsiting transaction."
  [name operation custom-sampling-context sentry-trace-header]
  (if sentry-trace-header
    (let [contexts (TransactionContext/fromSentryTrace name operation (io.sentry.SentryTraceHeader. sentry-trace-header))]
      (-> (Sentry/getCurrentHub)
          (.startTransaction contexts ^CustomSamplingContext custom-sampling-context true)))
    (-> (Sentry/getCurrentHub)
        (.startTransaction ^String name "http.server" ^CustomSamplingContext custom-sampling-context true))))

(defn swap-scope-request!
  "Set request info to the scope."
  [^Scope scope req]
  (.setRequest scope req))

(defn add-event-processor
  "Add Event Processor to the scope.
   event-processor is executed when tracing transaction finish or capture error event."
  [^Scope scope ^EventProcessor event-processor]
  (.addEventProcessor scope event-processor))

(defn swap-transaction-status!
  "Set trace transaction status."
  [^ITransaction transaction status]
  (.setStatus transaction status))

(defn finish-transaction!
  "Finish trace transaction and send event to Sentry."
  [^ITransaction transaction]
  (.finish transaction))

(defmacro with-start-child-span
  "Start a child span which has the operation or description
   and finish after evaluating forms."
  [operation description & forms]
  `(when-let [sp# (Sentry/getSpan)]
     (let [inner-sp# (.startChild sp# ~operation ~description)]
       (try
        ~@forms
        (.setStatus inner-sp# SpanStatus/OK)
        (catch Throwable e#
          (.setThrowable inner-sp# e#)
          (.setStatus inner-sp# SpanStatus/INTERNAL_ERROR)
          (throw e#))
        (finally
         (.finish inner-sp#))))))
