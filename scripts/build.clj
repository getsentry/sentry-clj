(ns scripts.build
  (:require
   [clojure.tools.build.api :as b]
   [org.corfield.build :as bb]))

(def ^:private version (format "6.3.%s" (b/git-count-revs nil)))
(def ^:private library 'io.sentry/sentry-clj)

(defn run-tests
  "Tests the application.

   This task will compile and test the application.
   "
  [opts]
  (-> (merge {:main-args ["-m" "kaocha.runner"]} opts)
      (bb/run-tests)))

(defn jar
  "JAR the artifact.

   This task will create the JAR in the `target` directory.
   "
  [{:keys [tag] :or {tag version} :as opts}]
  (-> opts
      (assoc :lib library :version tag :tag tag)
      (bb/clean)
      (bb/jar)))

(defn deploy
  "Deploy the JAR to your local repository (proxy).

   This task will build and deploy the JAR to your
   local repository using `deps-deploy`. This requires
   the following environment variables being set beforehand:

   CLOJARS_URL, CLOJARS_USERNAME, CLOJARS_PASSWORD

   Even although they are CLOJARS environment variables, they
   can actually point to anywhere, like your own Nexus OSS repository
   or an Artifactory repository for example.

   You may want to consider something like `direnv` to manage your
   per-directory loading of environment variables.
   "
  [{:keys [tag] :or {tag version} :as opts}]
  (-> opts
      (assoc :lib library :version tag :tag tag)
      (bb/deploy)))

(defn install
  "Deploy the JAR to your local .m2 directory"
  [{:keys [tag] :or {tag version} :as opts}]
  (-> opts
      (assoc :lib library :version tag :tag tag)
      (bb/install)))
