(ns lob-asset-management.db.portfolio
  (:require [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]))

(defn already-exist-asset?
  [ticket db-data]
  (if (empty? db-data)
    false
    (let [db-data-tickets (->> db-data (map :portfolio/ticket) set)]
      (contains? db-data-tickets ticket))))

(defn remove-already-exist-asset
  [assets-keep asset-filtered]
  (remove #(already-exist-asset? (:portfolio/ticket %) assets-keep) asset-filtered))

(defn update!
  [portfolio]
  (let [db-data (io.f-in/get-file-by-entity :portfolio)]
    (->> []
         (or db-data)
         (remove-already-exist-asset portfolio)
         (concat (or portfolio []))
         (sort-by :portfolio/percentage >)
         io.f-out/upsert)))

(defn overwrite!
  [portfolio]
  (io.f-out/upsert portfolio))

(defn delete                                                ;TODO : util for incorporation
  []
  (throw (ex-info :not-implemented "implementation pending")))