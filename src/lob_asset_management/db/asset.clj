(ns lob-asset-management.db.asset
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.aux.xtdb :refer [db-node] :as aux.xtdb]
            [lob-asset-management.aux.util :refer [string->uuid]]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.logic.asset :as l.a]
            [lob-asset-management.relevant :refer [asset-more-info config]]
            [xtdb.api :as xt]))

(defn ->internal
  [assets]
  (->> assets
       (map (fn [asset]
              (let [{:xt/keys [id] :as asset'} (first asset)]
                (-> asset'
                    (assoc :asset/id id)
                    (dissoc :xt/id)))))))

(defmulti get-all (fn [] (or (:env config) :dev)))

(defmethod get-all :dev
  []
  (try
    (io.f-in/get-file-by-entity :asset)
    (catch Exception e
      (throw (ex-info "Error when getting asset information" {:cause e})))))

(defmethod get-all :prod
  []
  (->> '{:find  [(pull ?e [*])]
         :where [[?e :asset/id _]]}
       (aux.xtdb/get! db-node)
       (map #(dissoc % :xt/id))))

(defmulti upsert-bulk! (fn [_] (or (:env config) :dev)))

(defn- maybe-upsert!
  [db-data assets]
  (when (not= db-data assets)
    (log/info "[UPDATE ASSET] New assets to be registered")
    (io.f-out/upsert assets)
    assets))

(defn remove-already-exist
  [assets-keep asset-filtered]
  (remove #(l.a/already-exist? (:asset/ticket %) assets-keep) asset-filtered))

(defmethod upsert-bulk! :dev
  [assets]
  (try
    (let [db-data (or (get-all) [])]
      (->> db-data
           (remove-already-exist assets)
           (concat (or assets []))
           (sort-by :asset/name)
           (maybe-upsert! db-data)))
    (catch Exception e
      (throw (ex-info "ASSET UPSERT ERROR" {:cause e})))))

(defn ticket->db-id
  [{:asset/keys [ticket] :as assets}]
  (assoc assets :xt/id (-> ticket name string->uuid)))

(defmethod upsert-bulk! :prod
  [assets]
  (->> assets
       (map ticket->db-id)
       (aux.xtdb/upsert! db-node))
  (xt/sync db-node))

(defn upsert!
  "CAUTION! => Overwrite the document with same id"
  [asset]
  (upsert-bulk! (list asset)))

(defn get-fixed-info-by-ticket
  [ticket]
  (get asset-more-info ticket))

(defmulti get-by-ticket (fn [_] (or (:env config) :dev)))

(defmethod get-by-ticket :dev
  [ticket]
  (->> (get-all)
       (filter #(= ticket (:asset/ticket %)))
       first))

(defmethod get-by-ticket :prod
  [ticket]
  (-> db-node
      xt/db
      (xt/q '{:find  [(pull ?e [*])]
              :in    [?t]
              :where [[?e :asset/ticket ?t]]}
            (-> ticket name clojure.string/upper-case keyword))
      ->internal
      first))

(comment
  ;MIGRATION
  (let [from (io.f-in/get-file-by-entity :asset)
        id->db-id (fn [{:asset/keys [ticket] :as asset}]
                    (assoc asset :xt/id (-> ticket name string->uuid)))]
    (->> from
         (map id->db-id)
         (aux.xtdb/upsert! db-node))
    (xt/sync db-node))

  ;DELETE ALL
  (let [from (io.f-in/get-file-by-entity :asset)]
    (doseq [a from]
      (aux.xtdb/delete! db-node (-> a :asset/ticket name string->uuid))))
  )
