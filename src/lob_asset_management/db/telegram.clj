(ns lob-asset-management.db.telegram
  (:require [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [schema.core :as s]))

(s/defschema TelegramMessage
  {:telegram/id         s/Uuid
   :telegram/message    s/Str
   :telegram/created-at s/Str
   :telegram/category   s/Str
   :telegram/active     s/Bool})

(defn get-all
  []
  (io.f-in/get-file-by-entity :telegram))

(s/defn insert
  [msg :- TelegramMessage]
  (let [db-data (or (get-all) [])
        msg' (if (map? msg) (list msg) msg)]
    (->> msg' (concat db-data) io.f-out/upsert)))