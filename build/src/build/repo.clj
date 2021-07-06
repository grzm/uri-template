(ns build.repo
  (:import
   (org.apache.maven.settings DefaultMavenSettingsBuilder Settings Server)
   (org.apache.maven.settings.building DefaultSettingsBuilderFactory)))

(set! *warn-on-reflection* true)

(defn set-settings-builder
  "From clojure.tools.deps.alpha.util.maven"
  [^DefaultMavenSettingsBuilder default-builder settings-builder]
  (doto (.. default-builder getClass (getDeclaredField "settingsBuilder"))
    (.setAccessible true)
    (.set default-builder settings-builder)))

(defn get-settings
  "From clojure.tools.deps.alpha.util.maven"
  ^Settings []
  (.buildSettings
   (doto (DefaultMavenSettingsBuilder.)
     (set-settings-builder (.newInstance (DefaultSettingsBuilderFactory.))))))

(defn assoc-some
  "Associates a key k, with a value v in a map m, if and only if v is not nil.
   Copied from https://github.com/weavejester/medley"
  ([m k v]
   (if (nil? v) m (assoc m k v)))
  ([m k v & kvs]
   (reduce (fn [m [k v]] (assoc-some m k v))
           (assoc-some m k v)
           (partition 2 kvs))))

(defn get-repo-settings
  "From deps-deploy.deps-deploy"
  [repo mvn-repos]
  (when (contains? mvn-repos repo)
    (let [^Server server (->> (get-settings)
                              (.getServers)
                              (filter #(= repo (.getId ^Server %)))
                              first)]
      (assoc-some (get mvn-repos repo)
                  :username (.getUsername server)
                  :password (.getPassword server)))))

(defn get-server-creds [server-id]
  (let [settings (get-settings)]
    (when-let [^Server server (->> (.getServers settings)
                                   (filter #(= server-id (.getId ^Server %)))
                                   first)]
      (assoc-some {}
                  :username (.getUsername server)
                  :password (.getPassword server)))))
