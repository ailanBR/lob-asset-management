(ns lob-asset-management.aux.crypto
  (:import java.security.MessageDigest))

(defn hash-str [input]
  (let [md (MessageDigest/getInstance "SHA-256")
        bytes (.digest md (.getBytes input "UTF-8"))]
    (apply str (map #(format "%02x" %) bytes))))
