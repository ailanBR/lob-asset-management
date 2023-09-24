(ns lob-asset-management.db.asset-news
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.aux.xtdb :refer [db-node] :as aux.xtdb]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.logic.asset :as l.a]
            [lob-asset-management.relevant :refer [config]]
            [schema.core :as s]
            [xtdb.api :as xt]))

(s/defschema AssetNew
  #:asset-news{:ticket   s/Keyword
               :name     s/Str
               :id       s/Str
               :txt      s/Str
               :datetime s/Str
               :href     s/Str
               (s/optional-key :status) s/Keyword})

(defn ->internal
  [data]
  (map #(-> % first (dissoc :xt/id)) data))

(s/defn get-all-xtdb :- [(s/maybe [AssetNew])]
  []
  (->> '{:find  [(pull ?e [*])]
         :where [[?e :asset-news/id _]]}
       (aux.xtdb/get! db-node )
       (map #(dissoc % :xt/id))))

(s/defn get-all-file :- [(s/maybe [AssetNew])]
  []
  (try
    (io.f-in/get-file-by-entity :asset-news)
    (catch Exception e
      (throw (ex-info "Error when getting asset news information" {:cause e})))))

(s/defn get-all :- [(s/maybe [AssetNew])]
  []
  (condp = (:env config)
    :dev (get-all-file)
    :prod (get-all-xtdb)))

(defn id->db-id
  [{:asset-news/keys [id] :as asset-new}]
  (assoc asset-new :xt/id id))

(defn- maybe-upsert!
  [db-data assets]
  (when (not= db-data assets)
    (log/info "[UPDATE ASSET-NEWS] New assets to be registered")
    (io.f-out/upsert assets)
    assets))

(defn remove-already-exist
  [assets-keep asset-filtered]
  (remove #(l.a/already-exist? (:asset-news/id %) assets-keep) asset-filtered))

(s/defn upsert-bulk-file!
  [asset-news :- [AssetNew]]
  (try
    (let [db-data (or (get-all) [])]
      (->> asset-news
           (remove-already-exist db-data)
           (concat (or db-data []))
           (sort-by :asset-news/name)
           (maybe-upsert! db-data)))
    (catch Exception e
      (throw (ex-info "ASSET-NEWS UPSERT ERROR" {:cause e})))))

(s/defn upsert-bulk-xtdb!
  [asset-news :- [AssetNew]]
  (->> asset-news
      (map id->db-id)
      (aux.xtdb/upsert! db-node)))

(s/defn upsert-bulk!
  [asset-news :- AssetNew]
  (condp = (:env config)
    :dev (upsert-bulk-file! asset-news)
    :prod (upsert-bulk-xtdb! asset-news)))

(s/defn upsert-file!
  "CAUTION! => Overwrite the document with same id"
  [asset-new :- AssetNew]
  (->> asset-new
       list
       upsert-bulk-file!))

(s/defn upsert-xtdb!
  "CAUTION! => Overwrite the document with same id"
  [asset-new :- AssetNew]
  (->> asset-new
       list
       (map id->db-id)
       (aux.xtdb/upsert! db-node)))

(s/defn upsert!
  [asset-new :- AssetNew]
  (condp = (:env config)
    :dev (upsert-file! asset-new)
    :prod (upsert-xtdb! asset-new)))

(defn get-by-ticket-file
  ([ticket]
   (get-by-ticket-file ticket (get-all)))
  ([ticket db-data]
   (->> db-data
        (filter #(= ticket (:asset-news/ticket %)))
        first)))

(s/defn get-by-ticket-xtdb :- (s/maybe [AssetNew])
  [ticket :- s/Keyword]
  (-> db-node
      xt/db
      (xt/q '{:find  [(pull ?e [*])]
              :in    [?t]
              :where [[?e :asset-news/ticket t]]}
            (-> ticket name clojure.string/upper-case keyword))
      ->internal))

(s/defn get-by-ticket :- (s/maybe [AssetNew])
  [ticket :- s/Keyword]
  (condp = (:env config)
    :dev (get-by-ticket-file ticket)
    :prod (get-by-ticket-xtdb ticket)))
