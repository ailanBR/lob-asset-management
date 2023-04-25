(ns lob-asset-management.adapter.transaction
  (:require [schema.core :as s]
            [clojure.string :as string]
            [lob-asset-management.models.transaction :as m.t]
            [lob-asset-management.models.asset :as m.a]
            [lob-asset-management.logic.asset :as l.a]
            [java-time.api :as t])
  (:import (java.util UUID)))

(s/defn b3-exchange->transaction-exchange :- m.t/Exchange
  [ex :- s/Str]
  (condp = ex
    "NU INVEST CORRETORA DE VALORES S.A."  :NU
    "INTER DTVM LTDA"                      :INTER
    :else                                  :OTHER))

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
     :transaction/average-price    unit-price
     :transaction/quantity       quantity
     :transaction/exchange       (b3-exchange->transaction-exchange exchange)
     :transaction/operation-type operation-type
     :transaction/processed-at   (-> (t/local-date-time) str)}))

(defn movements->transactions
  ;Concat with the data stored
  ;DR 001 - Unique by ID and don't treat update/changes in the comparative
  ;TODO: Remove DR details from code
  ; 1 - Avoid duplicated records by ID
  ; 2 - Get the most updated data [HOW?]
  ;   a - don't necessary ?
  ;     [x] Yes, is the same data
  ;   b - in case of a data change ?
  ;     [x]b.1 - this flow don't treat that.
  ;     [ ]b.2 - create a strong ID to avoid quantity and unit value in the ID : ASSET+DT+
  ;
  ;
  ([mov]
   (movements->transactions mov ()))
  ([mov db-data]
   (->> mov
        (map movements->transaction)
        (concat db-data)
        (group-by :transaction/id)
        (map #(->> % val (sort-by :transaction/processed-at) last))
        (sort-by :transaction.asset/ticket))))