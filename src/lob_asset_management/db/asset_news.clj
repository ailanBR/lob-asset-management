(ns lob-asset-management.db.asset-news
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.aux.xtdb :refer [db-node] :as aux.xtdb]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.logic.asset :as l.a]
            [lob-asset-management.relevant :refer [config]]
            [lob-asset-management.models.asset-news :as m.an]
            [schema.core :as s]
            [xtdb.api :as xt]))

(defn ->internal
  [data]
  (map #(-> % first (dissoc :xt/id)) data))

(defmulti get-all (fn [] (or (:env config) :dev)))

(defmethod get-all :dev
  []
  (try
    (io.f-in/get-file-by-entity :asset-news)
    (catch Exception e
      (throw (ex-info "Error when getting asset news information" {:cause e})))))

(defmethod get-all :prod
  []
  (->> '{:find  [(pull ?e [*])]
         :where [[?e :asset-news/id _]]}
       (aux.xtdb/get! db-node)
       (map #(dissoc % :xt/id))))

(defmulti upsert-bulk! (fn [_] (or (:env config) :dev)))

(defn- maybe-upsert!
  [db-data assets]
  (when (not= db-data assets)
    (log/info "[UPDATE ASSET-NEWS] New assets to be registered")
    (io.f-out/upsert assets)
    assets))

(defn remove-already-exist
  [assets-keep asset-filtered]
  (remove #(l.a/already-exist? (:asset-news/id %) assets-keep) asset-filtered))

(s/defmethod upsert-bulk! :dev
  [asset-news :- [m.an/AssetNews]]
  (try
    (let [db-data (or (get-all) [])]
      (->> asset-news
           (remove-already-exist db-data)
           (concat (or db-data []))
           (sort-by :asset-news/name)
           (maybe-upsert! db-data)))
    (catch Exception e
      (throw (ex-info "ASSET-NEWS UPSERT ERROR" {:cause e})))))

(defn id->db-id
  [{:asset-news/keys [id] :as asset-news}]
  (assoc asset-news :xt/id id))

(s/defmethod upsert-bulk! :prod
  [asset-news :- [m.an/AssetNews]]
  (->> asset-news
      (map id->db-id)
      (aux.xtdb/upsert! db-node)))

(s/defn upsert!
  "CAUTION! => Overwrite the document with same id"
  [asset-news :- m.an/AssetNews]
  (upsert-bulk! (list asset-news)))

(defmulti get-by-ticket (fn [_] (or (:env config) :dev)))

(s/defmethod get-by-ticket :dev :- (s/maybe [m.an/AssetNews])
  [ticket :- s/Keyword]
  (let [db-data (get-all)]
    (->> db-data
         (filter #(= ticket (:asset-news/ticket %)))
         first)))

(s/defmethod get-by-ticket :prod :- (s/maybe [m.an/AssetNews])
  [ticket :- s/Keyword]
  (-> db-node
      xt/db
      (xt/q '{:find  [(pull ?e [*])]
              :in    [?t]
              :where [[?e :asset-news/ticket t]]}
            (-> ticket name clojure.string/upper-case keyword))
      ->internal))
