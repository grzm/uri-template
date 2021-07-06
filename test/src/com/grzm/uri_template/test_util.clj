(ns com.grzm.uri-template.test-util
  (:require
   [com.grzm.uri-template.impl :as impl])
  (:import
   (java.net URLDecoder)
   (java.nio.charset StandardCharsets)))

(defn pct-decode [s]
  (-> (URLDecoder/decode s StandardCharsets/UTF_8)))

(defn forgiving-pct-decode
  "Percent-decodes the given string, passing through '%' when it's not
  part of a pct-encoded triple.

  The URI Template encoding behavior for literals, reserved expansion,
  and fragment expansion allow unreserved, reserved, and pct-encoded
  triples to pass through, so literals and variable values, once
  expanded, aren't guaranteed to equal their given value when
  pct-decoded. To compare if the given value and the expanded value
  are the same (modulo encoding), we compare the percent-decoded
  values. Normally percent-decoding a value that has % that is not
  part of a pct-encoded triple would result in an error: given values
  may include such '%', so we need to pass them through as-is."
  [s]
  (-> (loop [[c & rem] (seq s)
             res []]
        (if-not c
          (apply str res)

          (cond
            (= \% c)
            (let [[a b :as next-two] (take 2 rem)]
              (if (and (impl/hexdig? a) (impl/hexdig? b))
                (recur (drop 2 rem) (-> res
                                        (conj c)
                                        (into next-two)))
                (recur rem (into res [\% \2 \5]))))

            :else
            (recur rem (conj res c)))))
      (pct-decode)))
