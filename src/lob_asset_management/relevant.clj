(ns lob-asset-management.relevant
  (:require [cheshire.core :refer :all]
            [lob-asset-management.aux.file :refer [file->edn
                                                   file->byte-array]]
            [mount.core :as mount :refer [defstate]]))

(def secrets (file->edn "./resources/secrets.edn"))

#_(def alpha-key (:alpha-vantage-key secrets))
#_(def telegram-key (:telegram-bot-key secrets))
#_(def telegram-personal-chat (:personal-chat secrets))
#_(def google-app-credential
  (-> secrets
      :google_application_credentials
      parse-string))

(defn get-secrets
  []
  (file->edn "./resources/secrets.edn"))

(def asset-more-info (file->edn "./resources/asset_fixed_info.edn"))

(defn incorporation-movements
  []
  (file->edn "./resources/incorporation_movement.edn"))

(defstate config :start (file->edn "./resources/config.edn"))

(def config-prod (-> "./resources/config.edn"
                     file->edn
                     (assoc :env :prod)))       ;default = dev

(defstate alpha-key :start (System/getenv "ALPHA_KEY"))     ;(:alpha-vantage-key (get-secrets)))
(defstate telegram-key :start (System/getenv "TELEGRAM_BOT_KEY")) ; (:telegram-bot-key (get-secrets)))
(defstate telegram-personal-chat :start (:personal-chat (get-secrets)))
;(defstate google-oauth (file->byte-array "./resources/oauth.json"))   ;TODO: Move to ENV


(comment
  (read-secrets)

  (println (System/getenv "TELEGRAM_BOT_KEY"))

  (println (System/getenv "GOOGLE_APPLICATION_CREDENTIALS"))

  (println (System/getenv "GG_C"))

  (let [my-var (System/getenv "TESTE_KEY")]
    (println my-var))


  )
