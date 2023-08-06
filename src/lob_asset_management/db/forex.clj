(ns lob-asset-management.db.forex
  (:require [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]))

(defn get-all
  []
  (io.f-in/get-file-by-entity :forex-usd))

(defn upsert!
  [updated-forex]
  (io.f-out/upsert updated-forex))