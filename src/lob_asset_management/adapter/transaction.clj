(ns lob-asset-management.adapter.transaction
  (:require [schema.core :as s]
            [clojure.string :as string]
            [lob-asset-management.models.transaction :as m.t]
            [lob-asset-management.logic.asset :as l.a]
            [java-time.api :as t]
            ;[clj-time.core :as t]
            ))

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
    (= movement-type "Rendimento") :income
    (= movement-type "Dividendo") :dividend
    (= movement-type "Leilão de Fração") :waste
    :else (-> (:movement-type mov)
              (string/replace " " "")
              string/lower-case
              keyword)))

(s/defn gen-transaction-id
  [{:keys [transaction-date unit-price quantity product exchange] :as b3-movement}]
  (let [operation-type (b3-type->transaction-type b3-movement)
        ticket (l.a/b3-ticket->asset-ticket product)
        exchange' (b3-exchange->transaction-exchange exchange)]
    (-> (str ticket "-" transaction-date "-" unit-price "-" quantity "-" operation-type "-" exchange')
        (string/replace "/" "")
        (string/replace ":" ""))))

(defn safe-number->bigdec [num]
  (if (number? num) (bigdec num) 0M))

(s/defn movements->transaction :- m.t/Transaction
  [{:keys [transaction-date unit-price quantity exchange product operation-total] :as b3-movement}]
  (let [operation-type (b3-type->transaction-type b3-movement)
        ticket (l.a/b3-ticket->asset-ticket product)]
    {:transaction/id             (gen-transaction-id b3-movement)
     :transaction/created-at     (str transaction-date)
     ;:transaction/asset          asset
     ;:transaction/asset-id       (UUID/randomUUID)
     :transaction.asset/ticket   ticket
     :transaction/average-price  (safe-number->bigdec unit-price)
     :transaction/quantity       (safe-number->bigdec quantity)
     :transaction/exchange       (b3-exchange->transaction-exchange exchange)
     :transaction/operation-type operation-type
     :transaction/processed-at   (-> (t/local-date-time) str)
     :transaction/operation-total (safe-number->bigdec operation-total)}))

(defn already-read-transaction
  [{:transaction/keys [id]} db-data]
  (if (empty? db-data)
    true
    (let [db-data-tickets (->> db-data (map :transaction/id) set)]
      (not (contains? db-data-tickets id)))))

(defn movements->transactions
  ([mov]
   (movements->transactions mov ()))
  ([mov db-data]
   (println "Processing adapter transactions...current transactions [" (count db-data) "]")
   (let [mov-transactions (->> mov
                           (map movements->transaction)
                           (group-by :transaction/id)
                           (map #(->> % val (sort-by :transaction/processed-at) last)))
         new-transactions (->> mov-transactions
                               (filter #(already-read-transaction % db-data))
                               (concat (or db-data []))
                               (sort-by :transaction.asset/ticket))]
     (println "Concluded adapter transactions... "
              "read transactions[" (count mov-transactions) "] "
              "result [" (count new-transactions) "]")
     new-transactions)))
