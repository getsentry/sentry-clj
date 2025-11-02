(ns scripts.build
  (:require
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as dd]))

(def ^:private library 'io.sentry/sentry-clj)

(defn ^:private the-version
  [patch]
  (format "8.25.%s" patch))

(defn ^:private pom-template
  [tag]
  [[:description "A very thin wrapper around the official Java library for Sentry."]
   [:url "https://github.com/getsentry/sentry-clj"]
   [:licenses
    [:license
     [:name "Eclipse Public License"]
     [:url "http://www.eclipse.org/legal/epl-v10.html"]]]
   [:scm
    [:url "https://github.com/getsentry/sentry-clj"]
    [:connection "scm:git:git://github.com/getsentry/sentry-clj.git"]
    [:developerConnection "scm:git:ssh://git@github.com/getsentry/sentry-clj.git"]
    [:tag tag]]])

(def ^:private revs (Integer/parseInt (b/git-count-revs nil)))
(def ^:private snapshot (the-version (format "%s-SNAPSHOT" (inc revs))))
(def ^:private class-dir "target/classes")
(def ^:private target "target")

(defn ^:private jar-opts
  [{:keys [version] :as opts}]
  (assoc opts
         :lib library
         :version version
         :jar-file (format "target/%s-%s.jar" library version)
         :basis (b/create-basis)
         :class-dir class-dir
         :target target
         :src-dirs ["src"]
         :pom-data (pom-template version)))

(defn jar
  "This task will create the JAR in the `target` directory."
  [{:keys [tag] :or {tag snapshot} :as opts}]
  (let [{:keys [jar-file] :as opts'} (jar-opts (assoc opts :version tag))]
    (println (format "Cleaning '%s'..." target))
    (b/delete {:path "target"})
    (println (format "Writing 'pom.xml'..."))
    (b/write-pom opts')
    (println (format "Copying source files to '%s'..." class-dir))
    (b/copy-dir {:src-dirs ["src" "resources"] :target-dir class-dir})
    (println (format "Building JAR to '%s'..." jar-file))
    (b/jar opts')
    (println "Finished."))
  opts)

(defn install
  [{:keys [tag] :or {tag snapshot} :as opts}]
  (let [{:keys [jar-file] :as opts'} (jar-opts (assoc opts :version tag))]
    (dd/deploy {:installer :local
                :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts' [:lib :class-dir]))})))

(defn publish
  [{:keys [tag] :or {tag snapshot} :as opts}]
  (let [{:keys [jar-file] :as opts'} (jar-opts (assoc opts :version tag))]
    (dd/deploy {:installer :remote
                :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts' [:lib :class-dir]))})))
