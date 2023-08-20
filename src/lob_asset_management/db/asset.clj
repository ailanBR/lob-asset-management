(ns lob-asset-management.db.asset
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.logic.asset :as l.a]))

(defn get-all
  []
  (io.f-in/get-file-by-entity :asset))

(defn- maybe-upsert!
  [db-data assets]
  (when (not= db-data assets)
    (log/info "[UPDATE ASSET] New assets to be registered")
    (io.f-out/upsert assets)
    assets))

(defn remove-already-exist
  [assets-keep asset-filtered]
  (remove #(l.a/already-exist? (:asset/ticket %) assets-keep) asset-filtered))

(defn upsert!
  [assets]
  (let [db-data (or (get-all) [])]
    (->> db-data
         (remove-already-exist assets)
         (concat (or assets []))
         (sort-by :asset/name)
         (maybe-upsert! db-data))))

(defn get-by-ticket
  ([ticket]
   (get-by-ticket ticket (get-all)))
  ([ticket db-data]
   (->> db-data
        (filter #(= ticket (:asset/ticket %)))
        first)))