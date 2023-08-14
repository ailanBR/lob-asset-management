(ns lob-asset-management.db.portfolio
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]))

(defn get-all
  []
  (io.f-in/get-file-by-entity :portfolio))

(defn already-exist?
  [ticket db-data]
  (if (empty? db-data)
    false
    (let [db-data-tickets (->> db-data (map :portfolio/ticket) set)]
      (contains? db-data-tickets ticket))))

(defn remove-already-exist-asset
  [assets-keep asset-filtered]
  (remove #(already-exist? (:portfolio/ticket %) assets-keep) asset-filtered))

(defn maybe-upsert
  [db-data portfolio]
  (when (not= db-data portfolio)
    (log/info "[UPDATE PORTFOLIO] New portfolio record to be registered")
    (io.f-out/upsert portfolio)
    portfolio))

(defn update!
  [portfolio]
  (let [db-data (get-all)]
    (->> []
         (or db-data)
         (remove-already-exist-asset portfolio)
         (concat (or portfolio []))
         (sort-by :portfolio/percentage >)
         (maybe-upsert db-data))))

(defn overwrite!
  [portfolio]
  (io.f-out/upsert portfolio))

(defn delete                                                ;TODO : useful for incorporation events
  []
  (throw (ex-info :not-implemented "implementation pending")))