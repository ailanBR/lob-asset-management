(ns lob-asset-management.db.transaction
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.logic.transaction :as l.t]))

(defn get-all
  []
  (io.f-in/get-file-by-entity :transaction))

(defn- remove-already-exist
  [to-keep to-filter]
  (remove #(l.t/already-exist? (:transaction/id %) to-keep) to-filter))

(defn- maybe-upsert
  [db-data transactions]
  (when (not= db-data transactions)
    (log/info "[UPDATE TRANSACTION] New transactions to be registered")
    (io.f-out/upsert transactions)
    transactions))

(defn update!
  [transactions]
  (let [db-data (get-all)]
    (->> []
         (or db-data)
         (remove-already-exist transactions)
         (concat (or transactions []))
         (sort-by :transaction.asset/ticket)
         (maybe-upsert db-data))))

(defn get-by-id
  ([id]
   (get-by-id id (get-all)))
  ([id db-data]
   (->> db-data
        (filter #(= id (:transaction/id %)))
        first)))