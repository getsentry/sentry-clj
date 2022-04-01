(ns ring-with-tracing.main
  (:require
    [integrant.core :as ig]
    [ring.adapter.jetty :refer [run-jetty]]
    [ring.middleware.json :refer [wrap-json-params wrap-json-response]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [sentry-clj.core :as sentry]
    [sentry-clj.ring :refer [wrap-report-exceptions wrap-sentry-tracing]]
    [sentry-clj.tracing :refer [with-start-child-span]]))

(def config
  {:adapter/jetty {:port 8080, :handler (ig/ref :handler/router)}
   :handler/router {:app (ig/ref :handler/hello)}
   :handler/hello {:name "Sentry"}})

(defmethod ig/init-key :adapter/jetty [_ {:keys [handler] :as opts}]
  (run-jetty handler (-> opts (dissoc :handler) (assoc :join? false))))

(defmethod ig/init-key :handler/hello [_ {:keys [name]}]
  (fn [request]

    (with-start-child-span "task" "my-child-operation"
      (Thread/sleep 1000))

    {:status 200
     :headers {"Content-type" "application/json"}
     :body (str "Hi " name)}))

(defmethod ig/init-key :handler/router [_ {:keys [app]}]
  ;; set your dsn
  (let [dsn "https://abcdefg@localhost:9000/2"]
    (sentry/init! dsn {:dsn dsn
                       :environment "local"
                       :traces-sample-rate 1.0
                       :debug true}))
  
  (-> app
      (wrap-report-exceptions {})
      wrap-sentry-tracing
      wrap-keyword-params
      wrap-json-params
      wrap-json-response))

(defmethod ig/halt-key! :adapter/jetty [_ server]
  (.stop server))


(defn -main
  [& _]  
  (-> config
      ig/init))


