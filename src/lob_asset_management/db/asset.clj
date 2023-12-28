(ns lob-asset-management.db.asset
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.asset :as a.a]
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

(defn maintain-existing
  [db-data new-assets]
  (->> new-assets
       (remove-already-exist db-data)
       (concat (or db-data []))))

(defmethod upsert-bulk! :dev
  [assets]
  (try
    (let [db-data (or (get-all) [])]
      (->> assets
          (maintain-existing db-data)
          (sort-by :asset/name)
          (maybe-upsert! db-data)))
    (catch Exception e
      (throw (ex-info "ASSET UPSERT ERROR" {:cause e})))))

(defn ticket->db-id
  [{:asset/keys [ticket] :as assets}]
  (->> ticket a.a/ticket->asset-id (assoc assets :xt/id)))

(defmethod upsert-bulk! :prod
  [assets]
  (let [db-data (or (get-all) [])]
    (->> assets
         #_(remove-already-exist db-data)
         (mapv ticket->db-id)
         (aux.xtdb/upsert! db-node)))
  (xt/sync db-node)
  (get-all))

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

(defmulti get-with-retry (fn [] (or (:env config) :dev)))

(defmethod get-with-retry :dev
  []
  (->> (get-all)
       (filter :asset.market-price/retry-attempts)))

(defmethod get-with-retry :prod
  []
  (->> '{:find  [(pull ?e [*])]
         :where [[?e :asset.market-price/retry-attempts _]]}
       (aux.xtdb/get! db-node)
       (map #(dissoc % :xt/id))))

(defn snapshot
  []
  (->> (get-all)
       (sort-by :asset/name)
       (io.f-out/upsert)))

(comment
  ;MIGRATION
  (let [from (io.f-in/get-file-by-entity :asset)
        id->db-id (fn [{:asset/keys [ticket] :as asset}]
                    (assoc asset :xt/id (a.a/ticket->asset-id ticket)
                                 :asset/id (a.a/ticket->asset-id ticket)))]
    (->> from
         (map id->db-id)
         (aux.xtdb/upsert! db-node))
    (xt/sync db-node)
    )

  ;DELETE ALL
  (let [from (io.f-in/get-file-by-entity :asset)]
    (doseq [a from]
      (aux.xtdb/delete! db-node (-> a :asset/ticket name string->uuid))))

  ;-----------------
  (xt/sync db-node)
  (get-by-ticket :CNBS)
  (get-all)
  (-> db-node
      xt/db
      (xt/q '{:find  [(pull ?e [*])]
              :in    [?t]
              :where [[?e :asset/id ?t]]}
            (a.a/ticket->asset-id :CNBS))
      ->internal
      first
      )
  ;------------------
  (def s #:asset{:id #uuid"70201091-29ab-3765-bbb0-4de8cebc4cb8",
                 :name "SULA11 - SUL AMERICA S.A.",
                 :ticket :SULA11,
                 :category [:ti],
                 :type :stockBR,
                 :tax-number "29.978.814/0001-87"})
  (-> db-node
      xt/db
      (xt/q '{:find  [(pull ?e [*])]
              :in    [?t]
              :where [[?e :xt/id ?t]]}
            #uuid"3a371322-08d6-34cb-9ca8-1aa16ef5eb66")
      ->internal
      first)
  (def su (upsert! s))
  (get-by-ticket :SULA11)
  (string->uuid "CNBS")
  (upsert-bulk! su)
  (get-by-ticket :CNBS)


  )
