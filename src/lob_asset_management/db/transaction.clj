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
      (throw (ex-info "Error when getting transaction information" {:cause e})))))

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
(defmulti get-by-id (fn [_] (or (:env config) :dev)))

(defmethod get-by-id :dev
  [id]
  (->> (get-all)
       (filter #(= id (:transaction/id %)))))

(defmethod get-by-id :prod
  [id]
  (-> db-node
      xt/db
      (xt/q '{:find  [(pull ?e [*])]
              :in    [?id]
              :where [[?e :transaction/id ?id]]}
            id)
      (->> (map first))))

;-----------------------------------
(defn get-by-type
  [type]
  (-> db-node
      xt/db
      (xt/q '{:find  [(pull ?e [*])]
              :in    [?t]
              :where [[?e :transaction/operation-type ?t]]}
            type)
      (->> (map first))))


(defn update-asset-type
  [old-type new-type]
  (let [old-records (get-by-type old-type)
        new-records (map #(assoc % :transaction/operation-type new-type) old-records)]
    (doseq [records old-records]
      (aux.xtdb/delete! db-node (:xt/id records)))
    (upsert-bulk! new-records)))

(defn snapshot
  []
  (->> (get-all)
       (sort-by :transaction.asset/ticket)
       (io.f-out/upsert)))

(comment
  (defn get-by-type
    [type]
    (-> db-node
        xt/db
        (xt/q '{:find  [(pull ?e [*])]
                :in    [?t]
                :where [[?e :transaction/operation-type ?t]]}
              type)
        (->> (map first))))


  (def old #{:grupamento :desdobro :bonificaçãoemativos :resgate})
  (map #(get-by-type %) #{:grupamento :desdobro :bonificaçãoemativos :resgate})
  (clojure.pprint/print-table (map #(get-by-type %) #{:grupamento :desdobro :bonificaçãoemativos :resgate}))

  (clojure.pprint/print-table (get-by-type :direitosdesubscrição-excercído))

  (def updated (update-asset-type :atualização :update))

  (def old-name (get-by-type :desdobro))

  (def new-ones (map #(assoc % :transaction/operation-type :reverse-split) old-name))

  (upsert-bulk! new-ones)

  (map #(aux.xtdb/delete! db-node (:xt/id %)) old-name)


  (aux.xtdb/delete! db-node #uuid "77c7c482-fae9-3be4-a555-994e9cf4031b")

  (get-by-type :update)



  )
