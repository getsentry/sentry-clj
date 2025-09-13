(ns sentry-clj.logging
  "Structured logging integration with Sentry.
   
   Provides logging functions at all standard levels:
   - `trace`, `debug`, `info`, `warn`, `error`, `fatal` - Level-specific logging functions
   - `log` - Generic logging function accepting level as parameter, supports structured logging with maps
   
   ## Basic Usage
   ```clojure
   (info \"User logged in: %s\" username)
   (error \"Database error: %s\" (.getMessage ex))
   ```
   
   ## Generic Logging
   ```clojure
   (log :warn \"Service unavailable\")
   (log :error \"Failed operation: %s\" operation-name)
   ```
   
   ## Structured Logging
   ```clojure
   (log :fatal 
        {:user-id \"123\" :operation \"payment\" :critical true}
        \"Critical system failure\")
   ```"
  (:import [io.sentry Sentry SentryAttributes SentryLogLevel SentryDate SentryAttribute]
           [io.sentry.logger SentryLogParameters]))

(defn log-with-level
  "Log a message at the specified level with optional format arguments."
  [level message & args]
  (let [array-params (when (seq args)
                       (into-array Object args))
        logger (Sentry/logger)]
    (case level
      :trace (.trace logger message array-params)
      :debug (.debug logger message array-params)
      :info (.info logger message array-params)
      :warn (.warn logger message array-params)
      :error (.error logger message array-params)
      (throw (IllegalArgumentException. (str "Unknown log level: " level))))))

; Convenience functions that delegate to log!
(defn trace [message & args] (apply log-with-level :trace message args))
(defn debug [message & args] (apply log-with-level :debug message args))
(defn info [message & args] (apply log-with-level :info message args))
(defn warn [message & args] (apply log-with-level :warn message args))
(defn error [message & args] (apply log-with-level :error message args))

(defn- keyword->sentry-level
  "Converts keyword to SentryLogLevel enum."
  [level]
  (case level
    :trace SentryLogLevel/TRACE
    :debug SentryLogLevel/DEBUG
    :info SentryLogLevel/INFO
    :warn SentryLogLevel/WARN
    :error SentryLogLevel/ERROR
    :fatal SentryLogLevel/FATAL
    (if (instance? SentryLogLevel level)
      level
      (throw (IllegalArgumentException. (str "Unknown log level: " level))))))

(defn- log-parameters
  "Creates SentryLogParameters from a map of attributes.

   Automatically detects attribute types and creates appropriate SentryAttribute instances:
   - String values -> stringAttribute
   - Boolean values -> booleanAttribute
   - Integer values -> integerAttribute
   - Double/Float values -> doubleAttribute
   - Other values -> named attribute

   ## Example:
   ```clojure
   (log-parameters {:user-id \"123\"
                    :active true
                    :count 42
                    :score 98.5
                    :metadata {:key \"value\"}})
   ```"
  [attrs-map]
  (let [attributes (reduce-kv
                     (fn [acc k v]
                       (let [attr-name (name k)
                             attr (cond
                                    (string? v) (SentryAttribute/stringAttribute attr-name v)
                                    (boolean? v) (SentryAttribute/booleanAttribute attr-name v)
                                    (integer? v) (SentryAttribute/integerAttribute attr-name (long v))
                                    (or (double? v) (float? v)) (SentryAttribute/doubleAttribute attr-name (double v))
                                    :else (SentryAttribute/named attr-name v))]
                         (conj acc attr)))
                     []
                     attrs-map)]
    (SentryLogParameters/create
      (SentryAttributes/of (into-array SentryAttribute attributes)))))

(defn log
  "Generic logging function that accepts log level and optional parameters.

   ## Usage Examples

   ### Basic logging with level keyword:
   ```clojure
   (log :error \"Something went wrong\")
   (log :info \"User %s logged in from %s\" username ip-address)
   ```

   ### Structured logging with attributes:
   ```clojure
   (log :fatal
        {:user-id \"123\"
         :operation \"checkout\"
         :critical true
         :amount 99.99}
        \"Payment processing failed for user %s\"
        user-id)
   ```

   ### Logging with custom timestamp:
   ```clojure
   (log :warn
        (SentryInstantDate.)
        \"Delayed processing detected at %s\"
        (System/currentTimeMillis))
   ```

   ## Parameters
   - `level` - Log level keyword (`:trace`, `:debug`, `:info`, `:warn`, `:error`, `:fatal`) or SentryLogLevel enum
   - `args` - Message and optional parameters: either `[message & format-args]` or `[date-or-params message & format-args]`"
  [level & args]
  (let [sentry-level (keyword->sentry-level level)
        [first-arg second-arg & rest-args] args]
    (cond
      ; Basic case: (log :info "message" arg1 arg2)
      (and first-arg (string? first-arg))
      (let [message-params (drop 1 args)
            array-params (when (seq message-params) (into-array Object message-params))]
        (.log (Sentry/logger) sentry-level first-arg array-params))
      
      ; Structured case: (log :info {:attr "val"} "message" arg1 arg2)
      (and first-arg second-arg
           (or (map? first-arg) 
               (instance? SentryDate first-arg)
               (instance? SentryLogParameters first-arg)))
      (let [array-params (when (seq rest-args) (into-array Object rest-args))]
        (cond
          (instance? SentryDate first-arg)
          (.log (Sentry/logger) sentry-level ^SentryDate first-arg second-arg array-params)
          
          (instance? SentryLogParameters first-arg)
          (.log (Sentry/logger) sentry-level ^SentryLogParameters first-arg second-arg array-params)
          
          (map? first-arg)
          (.log (Sentry/logger) sentry-level ^SentryLogParameters (log-parameters first-arg) second-arg array-params)))
      
      :else
      (throw (IllegalArgumentException. 
              "Invalid arguments: expected [message & args] or [date-or-params message & args]")))))
