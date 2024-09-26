(ns lob-asset-management.db.telegram
  (:require [lob-asset-management.aux.xtdb :refer [db-node] :as aux.xtdb]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.models.telegram :as m.t]
            [lob-asset-management.relevant :refer [config]]
            [lob-asset-management.adapter.telegram :as a.t]
            [schema.core :as s]
            [xtdb.api :as xt]))

(defmulti get-all (fn [] (or (:env config) :dev)))

(defmethod get-all :dev
  []
  (io.f-in/get-file-by-entity :telegram))

(defmethod get-all :prod
  []
  (->> '{:find  [(pull ?p [*])]
         :where [[?p :telegram/id _]]}
       (aux.xtdb/get! db-node)
       (map #(dissoc % :xt/id))
       first))

(defmulti insert! (fn [_] (or (:env config) :dev)))

(defn new-message?
  [{:telegram/keys [message]} db-data]
  (->> db-data
       (filter #(= message (:telegram/message %)))
       first))

(s/defmethod insert! :dev
  [msg :- m.t/TelegramMessage]
  (let [db-data (or (get-all) [])
        msg' (if (map? msg) (list msg) msg)]
    (when (not (new-message? (first msg') db-data))
      (->> msg' (concat db-data) io.f-out/upsert))))

(defn id->db-id
  [{:telegram/keys [id] :as message}]
  (let [id' (or id (a.t/message->id message))]
    (assoc message :xt/id id' :telegram/id id')))

(defmethod insert! :prod
  [message]
  (->> message
       id->db-id
       list
       (aux.xtdb/upsert! db-node))
  (xt/sync db-node))
