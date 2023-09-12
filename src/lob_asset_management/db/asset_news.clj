(ns lob-asset-management.db.asset-news
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.logic.asset :as l.a]))

(defn get-all
  []
  (try
    (io.f-in/get-file-by-entity :asset-news)
    (catch Exception e
      (throw (ex-info "Error when getting asset news information" {:cause e})))))

(defn- maybe-upsert!
  [db-data assets]
  (when (not= db-data assets)
    (log/info "[UPDATE ASSET-NEWS] New assets to be registered")
    (io.f-out/upsert assets)
    assets))

(defn remove-already-exist
  [assets-keep asset-filtered]
  (remove #(l.a/already-exist? (:asset-news/id %) assets-keep) asset-filtered))

(defn upsert!
  [asset-news]
  (try
    (let [db-data (or (get-all) [])]
      (->> asset-news
           (remove-already-exist db-data)
           (concat (or db-data []))
           (sort-by :asset-news/name)
           (maybe-upsert! db-data)))
    (catch Exception e
      (throw (ex-info "ASSET-NEWS UPSERT ERROR" {:cause e})))))

(defn get-by-ticket
  ([ticket]
   (get-by-ticket ticket (get-all)))
  ([ticket db-data]
   (->> db-data
        (filter #(= ticket (:asset-news/ticket %)))
        first)))
