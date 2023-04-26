(ns lob-asset-management.controller.process-file
  (:require [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.adapter.transaction :as a.t]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.models.file :as m.f]))

(defn process-b3-movement
  [b3-movements]
  (let [db-assets (io.f-in/get-file-by-entity :asset)
        db-transactions (io.f-in/get-file-by-entity :transaction)
        assets (a.a/movements->assets b3-movements db-assets)
        transactions  (->> b3-movements
                           (map a.t/movements->transaction)
                           (filter #(not (contains? % db-transactions))))
        portfolio (a.p/transactions->portfolio transactions)]
    (map io.f-out/upsert [assets transactions portfolio])))

(defn process-b3-release
  [b3-file]
  (let [b3-movements (io.f-in/read-xlsx-by-file-name b3-file)]
    (process-b3-movement b3-movements)))

(defn process-b3-folder
  []
  (let [b3-movements (io.f-in/read-b3-folder)]
    (process-b3-movement b3-movements)))

(defn delete-all-files
  []
  (map io.f-in/delete-file m.f/list-file-name))