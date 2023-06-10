(ns lob-asset-management.io.storage
  (:require  [fire.core :as fire]
             [fire.auth :as auth]
             [clojure.string :as str]
             [environ.core :refer [env]]
             [cheshire.core :as json]))


(comment
  (def auth (auth/create-token "GOOGLE_APPLICATION_CREDENTIALS"))

  (println (System/getenv "GOOGLE_APPLICATION_CREDENTIALS"))
  (def t (System/getenv "GG_J"))

  (defn clean-env-var [env-var]
    (-> env-var
        (name)
        (str)
        (str/lower-case)
        (str/replace "_" "-")
        (str/replace "." "-")
        (keyword)))

  (defn decode [json-string]
    (json/decode json-string true))

  (-> "GG_J" clean-env-var env)
  )
