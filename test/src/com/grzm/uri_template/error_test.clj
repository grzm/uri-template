(ns com.grzm.uri-template.error-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.grzm.uri-template :refer [expand]]))

(def examples
  (->>
   [{:description "non-literal character outside of expression"
     :template "\""
     :vars {}
     :expected {:cognitect.anomalies/message "Unrecognized character."
                :error :unrecognized-character
                :character "\""
                :template "\""
                :idx 1}}
    {:description "non-literal character following expression"
     :template "{foo}\""
     :vars {}
     :expected {:cognitect.anomalies/message "Unrecognized character."
                :error :unrecognized-character
                :character "\""
                :template "{foo}\""
                :idx 6}}
    {:description "non-literal while parsing literal"
     :template "some\"stuff"
     :vars {}
     :expected {:cognitect.anomalies/message "Invalid literal character."
                :error :non-literal
                :character "\""
                :template "some\"stuff"
                :idx 5}}
    {:description "reserved operator"
     :template "{!foo}"
     :vars {}
     :expected {:cognitect.anomalies/message "Use of reserved operators is not supported."
                :error :reserved-operator
                :character "!"
                :template "{!foo}"
                :idx 2}}
    {:description "invalid percent-encoding in varname"
     :template "{fo%yzo}"
     :vars {}
     :expected {:cognitect.anomalies/message "Invalid percent-encoding in varname."
                :error :invalid-pct-encoding-char
                :character "y"
                :template "{fo%yzo}"
                :idx 5}}
    {:description "invalid percent-encoding in varname (second character)"
     :template "{fo%azo}"
     :vars {}
     :expected {:cognitect.anomalies/message "Invalid percent-encoding in varname."
                :error :invalid-pct-encoding-char
                :character "z"
                :template "{fo%azo}"
                :idx 6}}
    {:description "empty expression"
     :template "foo{}bar"
     :vars {}
     :expected {:cognitect.anomalies/category :cognitect.anomalies/incorrect
                :cognitect.anomalies/message "Empty expression not allowed."
                :error :empty-expression
                :character "}"
                :template "foo{}bar"
                :idx 5}}
    #_{:description "invalid character in expression"
       :template "{foo'bar}"
       :vars {}
       :expected {:cognitect.anomalies/category :cognitect.anomalies/incorrect
                  :cognitect.anomalies/message "Invalid character in expression"
                  :error :empty-expression
                  :character "'"
                  :template "{foo'bar}"
                  :idx 5}}
    {:description "invalid percent-encoding in varname (second character)"
     :template "{+'foo}"
     :vars {}
     :expected {:cognitect.anomalies/message "Invalid character in expression."
                :error :unrecognized-character
                :character "'"
                :template "{+'foo}"
                :idx 3}}
    {:description "invalid initial varname character"
     :template "{'foo}"
     :vars {}
     :expected {:cognitect.anomalies/message "Invalid initial varname character."
                :error :unrecognized-character
                :character "'"
                :template "{'foo}"
                :idx 2}}
    {:description "invalid start of varname charter following explode"
     :template "{foo*'}"
     :vars {}
     :expected {:cognitect.anomalies/message "Invalid initial varname character."
                :error :unrecognized-character
                :character "'"
                :template "{foo*'}"
                :idx 6}}
    {:description "invalid varname character in middle of varname"
     :template "{fo'o}"
     :vars {}
     :expected {:cognitect.anomalies/message "Invalid varname character."
                :error :unrecognized-character
                :character "'"
                :template "{fo'o}"
                :idx 4}}
    {:description "prefix out of bounds"
     :template "{foo:10000}"
     :vars {}
     :expected {:cognitect.anomalies/message "Prefix out of bounds. Prefix must be between 1 and 9999."
                :error :prefix-out-of-bounds
                :template "{foo:10000}"
                :idx 10}}
    {:description "invalid prefix character"
     :template "{foo:'}"
     :vars {}
     :expected {:cognitect.anomalies/message "Invalid prefix character."
                :error :unrecognized-character
                :character "'"
                :template "{foo:'}"
                :idx 6}}]
   (map #(assoc-in % [:expected :cognitect.anomalies/category] :cognitect.anomalies/incorrect))))

(deftest anomalies
  (doseq [ex examples]
    (testing (:description ex)
      (let [anom (expand (:template ex) (:vars ex))]
        (is (= (:expected ex) anom))))))
