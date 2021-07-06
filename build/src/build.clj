(ns build
  (:require
   [build.repo :as repo]
   [cemerick.pomegranate.aether :as aether]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deps-deploy]))

(set! *warn-on-reflection* true)

(def lib 'com.grzm/uri-template)
(def ^:const base-version "0.7")
(def snapshot-version (format "%s.%s-SNAPSHOT" base-version (b/git-count-revs nil)))
(def version (format "%s.%s" base-version (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def pom-file (format "%s/META-INF/maven/%s/%s/pom.xml" class-dir (namespace lib) (name lib)))
(def repos {:snapshot {:id "ossrh"
                       :url "https://oss.sonatype.org/content/repositories/snapshots/"}
            :release {:id "ossrh"
                      :url "https://oss.sonatype.org/service/local/staging/deploy/maven2/"}})

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn build [_]
  (clean nil)
  (jar nil))

(defn deploy-args
  ([repo]
   (deploy-args repo {}))
  ([{:keys [id url] :as _repo} args]
   (let [creds (repo/get-server-creds id)]
     (merge {:coordinates [lib version]
             :jar-file (io/file jar-file)
             :pom-file (io/file pom-file)
             :repository {id (assoc creds :url url)}}
            args))))

(defn deploy-snapshot [_]
  (let [{:keys [coordinates pom-file jar-file repository]
         :as _args} (deploy-args (:snapshot repos)
                                 {:coordinates [lib snapshot-version]})]
    (try
      (let [res (aether/deploy :coordinates coordinates
                               :jar-file jar-file
                               :pom-file pom-file
                               :repository repository)]
        (pprint/pprint (bean res)))
      (catch Throwable t
        (let [anom {:cognitect.anomalies/category :cognitect.anomalies/incorrect
                    :cognitect.anomalies/message "Something went wrong during deployment"
                    :t t}]
          (pprint/pprint anom)
          (throw t))))))

(defn deploy-release [_]
  (let [{:keys [id url]} (:release repos)
        creds (repo/get-server-creds id)
        repository {id (assoc creds :url url)}
        opts {:installer :remote
              :artifact jar-file
              :pom-file pom-file
              :sign-releases? true
              :repository repository}]
    (deps-deploy/deploy opts)))
