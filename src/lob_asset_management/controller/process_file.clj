(ns lob-asset-management.controller.process-file
  (:require [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.adapter.transaction :as a.t]))

;TODO: Consider the existent files (Concat with the existent)
(defn process-b3-movement
  [b3-movements]
  (let [db-assets (io.f-in/read-asset)
        db-transactions (io.f-in/read-transaction)
        assets (a.a/movements->assets b3-movements db-assets)
        transactions  (->> b3-movements
                           (map a.t/movements->transaction)
                           (filter #(not (contains? % db-transactions))))]
    (io.f-out/upsert assets)
    (io.f-out/upsert transactions)))

(defn process-b3-release
  [b3-file]
  (let [b3-movements (io.f-in/read-xlsx-by-file-name b3-file)]
    (process-b3-movement b3-movements)))

(defn process-b3-folder
  []
  (let [b3-movements (io.f-in/read-b3-folder)]
    (process-b3-movement b3-movements)))