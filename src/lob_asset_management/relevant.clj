(ns lob-asset-management.relevant
  (:require [lob-asset-management.aux.file :refer [file->edn]]))

(def secrets (file->edn "./resources/secrets.edn"))

(def alpha-key (:alpha-vantage-key secrets))
(def telegram-key (:telegram-bot-key secrets))
(def telegram-personal-chat (:personal-chat secrets))

(def asset-more-info (file->edn "./resources/asset_fixed_info.edn"))

(def configurations (file->edn "./resources/config.edn"))

(comment
  (read-secrets)

  (println (System/getenv "ALPHA_KEY"))

  (let [my-var (System/getenv "TESTE_KEY")]
    (println my-var))

  )