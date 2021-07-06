(ns com.grzm.uri-template.impl-test
  (:require
   [com.grzm.uri-template :as ut]
   [com.grzm.uri-template.impl :as impl]
   [clojure.test :refer [deftest is testing]]
   [clojure.walk :as walk]))

(defn parse-states
  ([template]
   (parse-states template nil))
  ([template n]
   (cond->> (reductions impl/advance (impl/initial-state) (impl/cp-seq template))
     n (take n)
     (nil? n) ((fn [states]
                 (let [penultimate (last states)]
                   (if (reduced? penultimate)
                     (-> (butlast states)
                         (conj (unreduced penultimate)))
                     (conj (vec states) (impl/finish-parse penultimate)))))))))

(deftest advance
  (testing "simple expansion failure"
    (let [template "{var"
          states (parse-states template)
          expected [{:idx 0, :state :start, :tokens []}
                    {:idx 1, :state :expression-started,
                     :token {:code-points (mapv int [\{]), :type :expression, :variables []},
                     :tokens []}
                    {:idx 2, :state :parsing-varname
                     :token {:code-points (mapv int [\{ \v]), :type :expression, :variables [],
                             :varspec {:code-points (mapv int [\v])}},
                     :tokens []}
                    {:idx 3, :state :parsing-varname,
                     :token {:code-points (mapv int  [\{ \v \a]), :type :expression, :variables [],
                             :varspec {:code-points (mapv int [\v \a])}},
                     :tokens []}
                    {:idx 4, :state :parsing-varname,
                     :token {:code-points (mapv int  [\{ \v \a \r]),
                             :type :expression, :variables [],
                             :varspec {:code-points (mapv int  [\v \a \r])}},
                     :tokens []}
                    {:idx 4
                     :cognitect.anomalies/category :cognitect.anomalies/incorrect
                     :error :early-termination
                     :state {:idx 4, :state :parsing-varname
                             :tokens []
                             :token {:code-points (mapv int  [\{ \v \a \r]),
                                     :type :expression,
                                     :variables []
                                     :varspec {:code-points (mapv int  [\v \a \r])}}}}]]
      (is (= expected states))))
  (testing "simple expansion"
    (let [template "{var}"
          states (parse-states template)
          expected [{:idx 0, :state :start, :tokens []}
                    {:idx 1, :state :expression-started,
                     :token {:code-points (mapv int [\{]), :type :expression, :variables []},
                     :tokens []}
                    {:idx 2, :state :parsing-varname
                     :token {:code-points (mapv int [\{ \v]), :type :expression, :variables [],
                             :varspec {:code-points (mapv int [\v])}},
                     :tokens []}
                    {:idx 3, :state :parsing-varname,
                     :token {:code-points (mapv int  [\{ \v \a]), :type :expression, :variables [],
                             :varspec {:code-points (mapv int [\v \a])}},
                     :tokens []}
                    {:idx 4, :state :parsing-varname,
                     :token {:code-points (mapv int  [\{ \v \a \r]),
                             :type :expression, :variables [],
                             :varspec {:code-points (mapv int  [\v \a \r])}},
                     :tokens []}
                    {:idx 5, :state :end-of-expr,
                     :tokens [{:code-points (mapv int  [\{ \v \a \r \}]),
                               :type :expression,
                               :variables [{:code-points (mapv int  [\v \a \r]) :varname "var"}]}]}
                    {:idx 5, :state :done,
                     :tokens [{:code-points (mapv int [\{ \v \a \r \}]),
                               :type :expression,
                               :variables [{:code-points (mapv int [\v \a \r])
                                            :varname "var"}]}]}]]
      (is (= expected states))))
  (testing "reserved expansion"
    (let [template "{+var}"
          states (parse-states template)
          expected [{:idx 0, :state :start, :tokens []}
                    {:idx 1, :state :expression-started,
                     :token {:code-points (mapv int [\{]), :type :expression, :variables []},
                     :tokens []}
                    {:idx 2, :state :found-operator
                     :token {:code-points (mapv int [\{ \+]), :type :expression, :variables [],
                             :op impl/PLUS_SIGN},
                     :tokens []}
                    {:idx 3, :state :parsing-varname,
                     :token {:code-points (mapv int [\{ \+ \v]), :type :expression, :variables [],
                             :op impl/PLUS_SIGN
                             :varspec {:code-points (mapv int [\v])}},
                     :tokens []}
                    {:idx 4, :state :parsing-varname,
                     :token {:code-points (mapv int [\{ \+ \v \a]), :type :expression, :variables [],
                             :op impl/PLUS_SIGN

                             :varspec {:code-points (mapv int [\v \a])}},
                     :tokens []}
                    {:idx 5, :state :parsing-varname,
                     :token {:code-points (mapv int [\{ \+ \v \a \r]), :type :expression, :variables [],
                             :op impl/PLUS_SIGN

                             :varspec {:code-points (mapv int [\v \a \r])}},
                     :tokens []}
                    {:idx 6, :state :end-of-expr,
                     :tokens [{:code-points (mapv int [\{ \+ \v \a \r \}]),
                               :op impl/PLUS_SIGN
                               :type :expression,
                               :variables [{:code-points (mapv int [\v \a \r]) :varname "var"}]}]}
                    {:idx 6, :state :done
                     :tokens [{:code-points (mapv int [\{ \+ \v \a \r \}]),
                               :op impl/PLUS_SIGN
                               :type :expression,
                               :variables [{:code-points (mapv int [\v \a \r]) :varname "var"}]}]}]]
      (is (= expected states)))))

(deftest variable-maps
  (let [keyword-map {:var "value"
                     :keys {:semi ";"
                            :dot "."
                            :comma ","}}
        string-map (walk/stringify-keys keyword-map)
        template  "{var}-{+keys*}"
        expected "value-semi=;,dot=.,comma=,"]
    (testing "keyword keys"
      (is (= expected (ut/expand template keyword-map))))
    (testing "string keys"
      (is (= expected (ut/expand template string-map))))
    (testing "boolean values"
      (let [vars {"falseval" false
                  "trueval" true
                  "nilval" nil
                  "list" ["a" nil "b"]
                  "keys" {"missing" nil
                          "comma" ","
                          "dot" "."}
                  "empty_list" []
                  "empty_keys" {}}]
        (is (= "?falseval=false&trueval=true" (ut/expand "{?falseval,trueval,nilval}" vars)))
        (is (= "a,b" (ut/expand "{list*}" vars)))
        (is (= "" (ut/expand "{+empty_list*}" vars)))
        (is (= "comma=,,dot=." (ut/expand "{+keys*}" vars)))
        (is (= "" (ut/expand "{empty_keys*}" vars)))))))

(deftest parse
  (testing "literal"
    (let [template "foo"
          variables {}
          expected {:parsed [{:code-points [102 111 111] :type :literal}]
                    :expansion "foo"}]
      (is (= (:parsed expected) (impl/parse template)))
      (is (= (:expansion expected) (impl/expand* (:parsed expected) variables)))))
  (testing "simple expression"
    (let [template "{foo}"
          expected-parse [{:code-points [123 102 111 111 125]
                           :type :expression
                           :variables [{:code-points [102 111 111]
                                        :varname "foo"}]}]
          parsed (impl/parse template)
          variables {"foo" "bar"}
          expected-expansion "bar"]
      (is (= expected-parse parsed))
      (is (= expected-expansion (impl/expand-expr variables (first expected-parse))))
      (is (= expected-expansion (impl/expand* expected-parse variables)))))
  (testing "simple expression needing encoding"
    (let [template "{fu}"
          expected-parse [{:code-points [123 102 117 125]
                           :type :expression
                           :variables [{:code-points [102 117]
                                        :varname "fu"}]}]
          parsed (impl/parse template)
          variables {"fu" "ふ"}
          expected-expansion "%E3%81%B5"]
      (is (= expected-parse parsed))
      (is (= expected-expansion (impl/expand* expected-parse variables)))))
  (testing "two expressions"
    (let [template "{foo,bar}"
          expected-parse [{:type :expression
                           :variables [{:code-points [102 111 111]
                                        :varname "foo"}
                                       {:code-points [98 97 114]
                                        :varname "bar"}]
                           :code-points [123 102 111 111 44 98 97 114 125]}]
          parsed (impl/parse template)
          variables {"foo" "FOO" "bar" "BAR"}
          expected-expansion "FOO,BAR"]
      (is (= expected-parse parsed))
      (is (= expected-expansion (impl/expand* expected-parse variables))))))
