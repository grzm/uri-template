(ns com.grzm.uri-template
  (:require
   [clojure.walk :as walk]
   [com.grzm.uri-template.impl :as impl]))

(set! *warn-on-reflection* true)

(defn expand
  [template variables]
  (try
    (let [parsed (impl/parse template)]
      (if (impl/anomaly? parsed)
        (cond-> parsed
          impl/*omit-state?* (dissoc parsed :state))
        (impl/expand* parsed (walk/stringify-keys variables))))
    (catch Throwable t
      {:cognitect.anomalies/category :cognitect.anomalies/fault
       :cognitect.anomalies/message "Unexpected error! Please report this anomaly."
       :template template
       :variables variables
       ::throwable t})))
