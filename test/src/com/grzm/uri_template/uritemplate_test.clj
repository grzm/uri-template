(ns com.grzm.uri-template.uritemplate-test
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [com.grzm.uri-template :as uri-template]
   [com.grzm.uri-template.impl :as impl]))

(defn load-json-resource [resource-name]
  (-> (io/resource resource-name)
      slurp
      json/read-str))

(defn stringify-keys [m]
  (->> m
       (map (fn [[k v]] [(name k) v]))
       (into {})))

(defn load-edn-resource [resource-name]
  (-> (io/resource resource-name)
      slurp
      (edn/read-string)
      ;; We want to be able to test Clojure keywords as map keys and Clojure values
      ;; as variable values, but we want the test setup structure to be the same
      ;; as the JSON examples, so we're limiting which keys we stringify.
      (->> (map (fn [[description test-setup]]
                  [description (stringify-keys test-setup)]))
           (into {}))))

(defn run-testcase [expand-fn variables [template expected]]
  (testing (format "template: %s" template)
    (let [expanded (expand-fn template variables)]
      (cond
        (coll? expected) (is ((set expected) expanded))
        :else (is (= expected expanded))))))

(defn run-test* [expand-fn test-cases]
  (doseq [[description {:strs [variables testcases]}] test-cases]
    (testing description
      (doseq [testcase testcases]
        (run-testcase expand-fn variables testcase)))))

(defn run-test-resource [expand-fn resource-name]
  (run-test* expand-fn (load-json-resource resource-name)))

(defn expand [template variables]
  (let [res (uri-template/expand template variables)]
    (if (or (impl/anomaly? res)
            (:error res))
      false
      res)))

(deftest spec-examples
  (run-test-resource expand "uritemplate-test/spec-examples.json"))

(deftest spec-examples-by-section
  (run-test-resource expand "uritemplate-test/spec-examples-by-section.json"))

(deftest extended-tests
  (run-test-resource expand "uritemplate-test/extended-tests.json"))

(deftest negative-tests
  (run-test-resource expand "uritemplate-test/negative-tests.json"))

(deftest extra-examples
  (run-test* expand (load-edn-resource "extra-examples.edn")))
