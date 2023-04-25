(ns lob-asset-management.controller.process-file
  (:require [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.adapter.transaction :as a.t]
            [lob-asset-management.logic.asset :as l.a]))

;TODO: Consider the existent files (Concat with the existent)
(defn process-file
  [b3-movements]
  (let [db-assets (io.f-in/read-asset)
        db-transactions (io.f-in/read-transaction)
        assets (a.a/movements->assets b3-movements db-assets)
        transactions  (->> b3-movements
                           (map a.t/movements->transaction)
                           (filter #(not (contains? % db-transactions))))]
    (io.f-out/upsert assets)
    (io.f-out/upsert transactions)
    transactions))

(defn process-b3-release
  [b3-file]
  (let [b3-movements (io.f-in/read-xlsx b3-file)
        db-assets (io.f-in/read-asset)
        assets (a.a/movements->assets b3-movements db-assets)
        db-transactions (io.f-in/read-transaction)
        transactions  (->> b3-movements
                         (map a.t/movements->transaction)
                         (filter #(not (contains? % db-transactions))))]
    (io.f-out/upsert assets)
    (io.f-out/upsert transactions)
    transactions))
