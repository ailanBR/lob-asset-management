(ns lob-asset-management.aux.money
  (:require [clojure.string :as string]))

(defn safe-big
  [value]
  (or value 0M))

(defn safe-dob
  [value]
  (or value 0.0))

(defn safe-number->bigdec
  [num]
  (if (number? num)
    (bigdec num)
    (let [formatted-input (-> num
                              str
                              (string/replace #"\$|R|,|\." "")
                              (string/replace " " "")
                              (string/replace "-" ""))]
      (if (empty? formatted-input)
        0M
        (let [formatted-input' (when (= (first formatted-input) \0)
                                 (str (first formatted-input) "." (apply str (rest formatted-input))))
              formatted-input-bigdec (bigdec formatted-input')
              number-with-decimal-cases (if (>= formatted-input-bigdec 100M)
                                          (/ formatted-input-bigdec 100)
                                          formatted-input-bigdec)]
          number-with-decimal-cases)))))
