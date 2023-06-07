(ns lob-asset-management.io.storage
  (:require  [fire.core :as fire]
             [fire.auth :as auth]
             [lob-asset-management.relevant :refer [google-app-credential]]
             ))


(comment
  (def auth (auth/create-token "GG_T"))
  )
