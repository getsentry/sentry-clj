(ns dev
  (:require
   [integrant.core :as ig]
   [integrant.repl :refer [go halt]]
   [ring-with-tracing.main :as main]))

(integrant.repl/set-prep! #(ig/expand main/config))

(defn start
  []
  (go))

(defn stop
  []
  (halt))
