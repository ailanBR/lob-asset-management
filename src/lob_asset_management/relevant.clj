(ns lob-asset-management.relevant
  (:require [lob-asset-management.io.file-in :refer [file->edn]]))

(def secrets (file->edn "./resources/secrets.edn"))

(def alpha-key (:alpha-vantage-key secrets))

(comment
  (read-secrets)

  (println (System/getenv "ALPHA_KEY"))

  (let [my-var (System/getenv "TESTE_KEY")]
    (println my-var))

  )