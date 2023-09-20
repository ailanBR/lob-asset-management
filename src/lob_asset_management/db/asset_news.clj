(ns lob-asset-management.db.asset-news
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.aux.xtdb :refer [db-node] :as aux.xtdb]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.logic.asset :as l.a]
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

(s/defn get-all :- [(s/maybe [AssetNew])]
  []
  (aux.xtdb/get! db-node '{:find  [(pull ?e [*])]
                            :where [[?e :asset-news/id _]]}))

(s/defn upsert-bulk!
  [asset-news :- [AssetNew]]
  (aux.xtdb/upsert! db-node asset-news))

(s/defn upsert!
  [asset-new :- AssetNew]
  (aux.xtdb/upsert! db-node asset-new))

(s/defn get-by-ticket :- (s/maybe [AssetNew])
  [ticket :- s/Keyword]
  (xt/q (xt/db db-node) '{:find  [(pull e [*])]
                          :in    [t]
                          :where [[e :asset-news/ticket t]]}
        ticket))
;=================== Using local files
#_(defn get-all
    []
    (try
      (io.f-in/get-file-by-entity :asset-news)
      (catch Exception e
        (throw (ex-info "Error when getting asset news information" {:cause e})))))

#_(defn- maybe-upsert!
  [db-data assets]
  (when (not= db-data assets)
    (log/info "[UPDATE ASSET-NEWS] New assets to be registered")
    (io.f-out/upsert assets)
    assets))

#_(defn remove-already-exist
  [assets-keep asset-filtered]
  (remove #(l.a/already-exist? (:asset-news/id %) assets-keep) asset-filtered))

#_(defn upsert!
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

#_(defn get-by-ticket
  ([ticket]
   (get-by-ticket ticket (get-all)))
  ([ticket db-data]
   (->> db-data
        (filter #(= ticket (:asset-news/ticket %)))
        first)))
