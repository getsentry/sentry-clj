(defproject io.sentry/sentry-clj "0.5.0-SNAPSHOT"
  :description "A Clojure client for Sentry."
  :url "https://github.com/getsentry/sentry-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[cheshire "5.7.1"]
                 [clj-time "0.13.0"]
                 [io.sentry/sentry "1.2.0"]
                 [ring/ring-core "1.6.1" :scope "provided"]]
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]
  :profiles {:dev [:project/dev :profiles/dev]
             :test [:project/test :profiles/test]
             :profiles/dev {:dependencies [[mocko "0.2.3"]
                                           [org.clojure/clojure "1.8.0"]
                                           [org.slf4j/slf4j-jcl "1.7.25"]]}
             :profiles/test {}
             :project/dev {:source-paths ["dev"]
                           :repl-options {:init-ns user}}
             :project/test {:dependencies []}})
