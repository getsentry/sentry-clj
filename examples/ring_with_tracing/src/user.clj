(ns user
  (:require
    [integrant.core :as ig]
    [integrant.repl :refer [clear go halt prep init reset reset-all]]
    [ring-with-tracing.main]))


(integrant.repl/set-prep! (comp ig/prep (constantly ring-with-tracing.main/config)))
