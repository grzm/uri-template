(ns ^:gen com.grzm.uri-template.expansion-test
  (:require
   [clojure.core :as core]
   [clojure.string :as str]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [com.grzm.uri-template :as uri-template]
   [com.grzm.uri-template.impl :as impl]
   [com.grzm.uri-template.test-util :as test-util]))

;;;; generators for strings

(def digit
  (gen/fmap core/char (gen/choose 48 57)))

(def hexdig
  (gen/frequency [[10 digit]
                  [3 (gen/elements [\a \b \c \d \e \f])]
                  [3 (gen/elements [\A \B \C \D \E \F])]]))

(def pct-encoded-triple
  (gen/no-shrink
   (gen/fmap (fn [digits]
               (apply format "%%%s%s" digits))
             (gen/vector hexdig 2))))

(def char-unreserved
  (gen/one-of [gen/char-alpha-numeric (gen/elements [\- \. \_ \~])]))

(def char-gen-delim
  (gen/elements [\: \/ \? \# \[ \] \@]))

(def char-sub-delim
  (gen/elements [\! \$ \& \' \( \) \* \+ \, \; \=]))

(def char-reserved
  (gen/one-of [char-gen-delim char-sub-delim]))

(defn expand-interval [[from thru]]
  (range from (inc (or thru from))))

(def char-string-literal-ascii
  (gen/fmap impl/cp-str
            (->> [[0x21]
                  [0x23 0x24]
                  [0x25]
                  [0x26]
                  [0x28 0x3B]
                  [0x3D]
                  [0x3F 0x5B]
                  [0x5D]
                  [0x5F]
                  [0x61 0x7A]
                  [0x7E]]
                 (mapcat expand-interval)
                 (gen/elements))))

(def char-string
  (gen/fmap impl/cp-str
            (gen/elements (expand-interval [1 255]))))

(def char-string-ucschar
  (gen/fmap impl/cp-str
            (->> [[0xA0 0xD7FF]
                  [0xF900 0xFDCF]
                  [0xFDF0 0xFFEF]
                  [0x10000 0x1FFFD]
                  [0x20000 0x2FFFD]
                  [0x30000 0x3FFFD]
                  [0x40000 0x4FFFD]
                  [0x50000 0x5FFFD]
                  [0x60000 0x6FFFD]
                  [0x70000 0x7FFFD]
                  [0x80000 0x8FFFD]
                  [0x90000 0x9FFFD]
                  [0xA0000 0xAFFFD]
                  [0xB0000 0xBFFFD]
                  [0xC0000 0xCFFFD]
                  [0xD0000 0xDFFFD]
                  [0xE1000 0xEFFFD]]
                 (map #(apply gen/choose %))
                 (gen/one-of))))

(def char-string-iprivate
  (gen/fmap impl/cp-str
            (->> [[0xE000 0xF8FF]
                  [0xF0000 0xFFFFD]
                  [0x100000 0x10FFFD]]
                 (map #(apply gen/choose %))
                 (gen/one-of))))

(def char-string-literal
  (gen/frequency [[1 char-string-ucschar]
                  [1 char-string-iprivate]
                  [5 pct-encoded-triple]
                  [40 char-string-literal-ascii]]))

(def char-string-utf8
  (gen/frequency [[1 char-string-ucschar]
                  [1 char-string-iprivate]
                  [5 pct-encoded-triple]
                  [40 char-string]]))

(def literals
  (gen/fmap str/join (gen/vector char-string-literal)))

(def string-utf8
  (gen/fmap str/join (gen/vector char-string-utf8)))

(def decoded-string-equals-u+r-encoded-decoded-string
  (prop/for-all [s literals]
                (let [decoded (test-util/forgiving-pct-decode s)
                      encoded-decoded (-> s impl/U+R test-util/pct-decode)]
                  (= decoded encoded-decoded))))

(defspec round-trip-u+r-literal 10000 decoded-string-equals-u+r-encoded-decoded-string)

(defspec literal-expansion-never-fails
  10000
  (prop/for-all [s literals]
                (let [res (uri-template/expand s {})]
                  (not (map? res)))))

;;      expression    =  "{" [ operator ] variable-list "}"
;;      operator      =  op-level2 / op-level3 / op-reserve
;;      op-level2     =  "+" / "#"
;;      op-level3     =  "." / "/" / ";" / "?" / "&"
;;      op-reserve    =  "=" / "," / "!" / "@" / "|"

(def char-string-operator
  (->> (keys impl/op-code-points)
       (map str)
       (into [""])
       (gen/elements)))

;;      variable-list =  varspec *( "," varspec )
;;      varspec       =  varname [ modifier-level4 ]
;;      varname       =  varchar *( ["."] varchar )
;;      varchar       =  ALPHA / DIGIT / "_" / pct-encoded

(def maybe-char-string-operator
  (gen/frequency [[3 char-string-operator]
                  [1 (gen/return "")]]))

(def varchar*
  (gen/fmap impl/cp-str
            (gen/one-of [(gen/choose 48 57)
                         (gen/choose 65 90)
                         (gen/choose 97 122)])))

(def varchar
  (gen/frequency [[10 varchar*]
                  [1 (gen/return "_")]
                  [1 pct-encoded-triple]]))

(def continuing-varchar
  (gen/frequency [[1 (gen/elements ["." ""])]
                  [10 varchar]]))

(def varname
  (gen/fmap (fn [[a bs]]
              (str a (apply str bs)))
            (gen/tuple varchar (gen/vector continuing-varchar))))

(def prefix-modifier
  (gen/fmap (partial str ":")
            (gen/such-that pos? gen/nat)))

(def maybe-modifier-level4
  (gen/one-of [(gen/return "")
               prefix-modifier
               (gen/return "*")]))

(def varspec
  (gen/fmap str/join
            (gen/tuple varname
                       maybe-modifier-level4)))

(defn join-varspecs [varspecs]
  (str/join "," varspecs))

(def variable-list
  (gen/fmap join-varspecs
            (gen/such-that not-empty
                           (gen/vector varspec))))

(defn wrap-expression
  [[op v-list]]
  (str "{" op v-list "}"))

(def expression
  (gen/fmap wrap-expression
            (gen/tuple char-string-operator
                       variable-list)))

(def template
  (gen/fmap str/join
            (gen/vector (gen/one-of [literals expression]))))

;; this gets slow fast
(defspec ^:slow expand-never-throws-on-template 100
  (prop/for-all [t template]
                (let [vars {}
                      res (impl/expand t vars)]
                  (not (map? res)))))

(defspec expand-never-throws-on-string 100
  (prop/for-all [t template]
                (let [vars {}
                      res (impl/expand t vars)]
                  (not (map? res)))))

;; TODO Make more varied templates. These are just simple expressions.
(def template-and-variables
  (gen/bind (gen/map varname string-utf8 {:max-elements 10})
            (fn [vars]
              (let [t (->> (keys vars)
                           (map (fn [v] (format "{%s}" v)))
                           (str/join))]
                (gen/tuple (gen/return t) (gen/return vars))))))

(defspec no-fault-template-expansion 100
  (prop/for-all [[t vars] template-and-variables]
                (let [res (impl/expand t vars)]
                  (not (map? res)))))

(def interesting-values (gen/frequency [[5 string-utf8]
                                        [2 (gen/vector gen/any)]
                                        [2 (gen/map string-utf8 gen/any)]
                                        [1 gen/any]]))

(def interesting-templates-and-variables
  (gen/bind (gen/tuple (gen/vector varname 0 5)
                       (gen/vector varname 0 5)
                       (gen/vector varname 0 5)
                       (gen/vector maybe-char-string-operator 10)
                       (gen/vector literals 10)
                       (gen/vector interesting-values 10))
            (fn [[template-only vars-only both ops lits values]]
              (let [template (->> (concat template-only both)
                                  (interleave ops)
                                  (partition 2)
                                  (map (fn [[op v]] (format "{%s%s}" op v)))
                                  (interleave lits)
                                  (str/join))
                    vars (zipmap (concat vars-only both) values)]
                (gen/tuple (gen/return template)
                           (gen/return vars))))))

(defspec interesting-no-fault-template-expansion 1000
  (prop/for-all [[t vars] interesting-templates-and-variables]
                (let [res (impl/expand t vars)]
                  (not (map? res)))))

(comment

  @(def res (interesting-no-fault-template-expansion))
  (def interesting-val (-> (get-in res [:shrunk :smallest])
                           ffirst))

  (->> (get-in res [:shrunk :smallest])
       first
       (apply impl/expand))

  (->> (impl/cp-seq interesting-val)
       (map #(Character/getName %)))

  (Character/getName 1)

  :end)
