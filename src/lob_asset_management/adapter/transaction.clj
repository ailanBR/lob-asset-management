(ns lob-asset-management.adapter.transaction
  (:require [schema.core :as s]
            [clojure.string :as string]
            [lob-asset-management.models.transaction :as m.t]
            [lob-asset-management.logic.asset :as l.a]
            [java-time.api :as t]))

(s/defn b3-exchange->transaction-exchange :- m.t/Exchange
  [ex :- s/Str]
  (condp = ex
    "NU INVEST CORRETORA DE VALORES S.A."  :nu
    "INTER DTVM LTDA"                      :inter
    :else                                  :other))

(defn b3-sell? [mov]
  (and (= (:type mov) "Debito")
       (= (:movement-type mov) "Transferência - Liquidação")))

(defn b3-buy? [mov]
  (and (= (:type mov) "Credito")
       (= (:movement-type mov) "Transferência - Liquidação")))

(defn b3-type->transaction-type [{:keys [movement-type] :as mov}]
  (cond
    (b3-sell? mov) :sell
    (b3-buy? mov) :buy
    (= movement-type "Juros Sobre Capital Próprio") :JCP
    :else (-> (:movement-type mov)
              (string/replace " " "")
              string/lower-case
              keyword)))

(s/defn gen-transaction-id
  [{:keys [transaction-date unit-price quantity product exchange] :as b3-movement}]
  (let [operation-type (b3-type->transaction-type b3-movement)
        ticket (l.a/b3-ticket->asset-ticket product)]
    (-> (str ticket "-" transaction-date "-" unit-price "-" quantity "-" operation-type "-" exchange)
        (string/replace "/" "")
        (string/replace ":" ""))))

(s/defn movements->transaction :- m.t/Transaction
  [{:keys [transaction-date unit-price quantity exchange product] :as b3-movement}]
  (let [operation-type (b3-type->transaction-type b3-movement)
        ticket (l.a/b3-ticket->asset-ticket product)]
    {:transaction/id             (gen-transaction-id b3-movement)
     :transaction/created-at     transaction-date
     ;:transaction/asset          asset
     ;:transaction/asset-id       (UUID/randomUUID)
     :transaction.asset/ticket   ticket
     :transaction/average-price  (if (number? unit-price) (bigdec unit-price) 0M)
     :transaction/quantity       (if (number? quantity) (bigdec quantity) 0M)
     :transaction/exchange       (b3-exchange->transaction-exchange exchange)
     :transaction/operation-type operation-type
     :transaction/processed-at   (-> (t/local-date-time) str)}))

(defn movements->transactions
  ([mov]
   (movements->transactions mov ()))
  ([mov db-data]
   (->> mov
        (map movements->transaction)
        (concat db-data)
        (group-by :transaction/id)
        (map #(->> % val (sort-by :transaction/processed-at) last))
        (sort-by :transaction.asset/ticket))))