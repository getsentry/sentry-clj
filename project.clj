(defproject com.codahale/raven-clj "0.1.2"
  :description "A Clojure client for Sentry."
  :url "https://github.com/codahale/raven-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[cheshire "5.6.3"]
                 [clj-time "0.12.0"]
                 [com.getsentry.raven/raven "7.7.0"]
                 [ring/ring-core "1.5.0" :scope "optional"]]
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]
  :aot [raven-clj.internal]
  :profiles {:dev [:project/dev :profiles/dev]
             :test [:project/test :profiles/test]
             :profiles/dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                           [org.slf4j/slf4j-jcl "1.7.21"]]}
             :profiles/test {}
             :project/dev {:source-paths ["dev"]
                           :repl-options {:init-ns user}}
             :project/test {:dependencies []}})
