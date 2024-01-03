(ns lob-asset-management.db.transaction
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.transaction :as a.t]
            [lob-asset-management.aux.xtdb :refer [db-node] :as aux.xtdb]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.logic.transaction :as l.t]
            [lob-asset-management.relevant :refer [config]]
            [xtdb.api :as xt]))

(defmulti get-all (fn [] (or (:env config) :dev)))

(defmethod get-all :dev
  []
  (try
    (io.f-in/get-file-by-entity :transaction)
    (catch Exception e
      (throw (ex-info "Error when getting portfolio information" {:cause e})))))

(defmethod get-all :prod
  []
  (->> '{:find  [(pull ?p [*])]
         :where [[?p :transaction/id _]]}
       (aux.xtdb/get! db-node)
       (map #(dissoc % :xt/id))))

;-----------------------------------
(defmulti upsert-bulk! (fn [_] (or (:env config) :dev)))

(defn- remove-already-exist
  [to-keep to-filter]
  (remove #(l.t/already-exist? (:transaction/id %) to-keep) to-filter))

(defn- maybe-upsert!
  [db-data transactions]
  (when (not= db-data transactions)
    (log/info "[UPDATE TRANSACTION] New transactions to be registered")
    (io.f-out/upsert transactions)
    transactions))

(defmethod upsert-bulk! :dev
  [transactions]
  (let [db-data (get-all)]
    (->> []
         (or db-data)
         (remove-already-exist transactions)
         (concat (or transactions []))
         (sort-by :transaction.asset/ticket)
         (maybe-upsert! db-data))))

(defn id->db-id
  [transaction]
  (let [id (a.t/transaction->id transaction)]
    (assoc transaction :xt/id id
                       :transaction/id id)))

(defmethod upsert-bulk! :prod
  [transaction]
  (->> transaction
       (map id->db-id)
       (aux.xtdb/upsert! db-node))
  (xt/sync db-node)
  (get-all))

;-----------------------------------
(defmulti get-by-ticket (fn [_] (or (:env config) :dev)))

(defmethod get-by-ticket :dev
  [ticket]
  (->> (get-all)
       (filter #(= ticket (:transaction.asset/ticket %)))))

(defmethod get-by-ticket :prod
  [ticket]
  (-> db-node
      xt/db
      (xt/q '{:find  [(pull ?e [*])]
              :in    [?t]
              :where [[?e :transaction.asset/ticket ?t]]}
            (-> ticket name clojure.string/upper-case keyword))
      (->> (map first))))

;-----------------------------------
(defn snapshot
  []
  (->> (get-all)
       (sort-by :transaction.asset/ticket)
       (io.f-out/upsert)))

(comment

  )
