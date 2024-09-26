(ns lob-asset-management.db.forex
  (:require [lob-asset-management.aux.xtdb :refer [db-node] :as aux.xtdb]
            [lob-asset-management.aux.util :refer [string->uuid]]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.relevant :refer [config]]
            [xtdb.api :as xt]))

(defmulti get-all (fn [] (or (:env config) :dev)))

(defmethod get-all :dev
  []
  (io.f-in/get-file-by-entity :forex-usd))

(defmethod get-all :prod
  []
  (->> '{:find  [(pull ?p [*])]
         :where [[?p :forex/id _]]}
       (aux.xtdb/get! db-node)
       (map #(dissoc % :xt/id))
       first))

(defmulti upsert! (fn [_] (or (:env config) :dev)))

(defmethod upsert! :dev
  [updated-forex]
  (io.f-out/upsert updated-forex))

(defn ->db-id
  [updated-forex]
  (let [id (string->uuid "forex-price")]
    (assoc updated-forex :xt/id id :forex/id id)))

(defmethod upsert! :prod
  [updated-forex]
  (->> updated-forex
       ->db-id
       list
       (aux.xtdb/upsert! db-node))
  (xt/sync db-node))

(defn snapshot
  []
  (io.f-out/upsert (get-all)))

(comment
  (:env config)
  (get-all)

  (aux.xtdb/get! db-node
                 '{:find  [(pull ?p [*])]
                   :where [[?p :xt/id #uuid"30f57d69-fa8d-3b9c-b843-aa62a3468f83"]]})

  (string->uuid "forex-price")
  (aux.xtdb/delete! db-node #uuid"30f57d69-fa8d-3b9c-b843-aa62a3468f83")
  )
