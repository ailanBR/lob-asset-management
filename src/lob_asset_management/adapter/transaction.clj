(ns lob-asset-management.adapter.transaction
  (:require [clojure.tools.logging :as log]
            [schema.core :as s]
            [clojure.string :as string]
            [lob-asset-management.models.transaction :as m.t]
            [lob-asset-management.adapter.asset :as a.a]
            [java-time.api :as t]
            [lob-asset-management.aux.util :as aux.u]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.aux.money :refer [safe-number->bigdec]]
            [lob-asset-management.logic.transaction :as l.t]))

(s/defn movement-exchange->transaction-exchange :- m.t/Exchange
  [ex :- s/Str]
  (condp = ex
    "NU INVEST CORRETORA DE VALORES S.A."                       :nu
    "INTER DTVM LTDA"                                           :inter
    "INTER DISTRIBUIDORA DE TITULOS E VALORES MOBILIARIOS LTDA" :inter
    "Sproutfy"                                                  :sproutfy
    (-> ex string/lower-case keyword)))

(defn movement-type->transaction-type [{:keys [movement-type] :as mov}]
  (cond
    (l.t/sell? mov) :sell
    (l.t/buy? mov) :buy
    (= movement-type "Juros Sobre Capital Próprio") :JCP
    (= movement-type "Rendimento") :income
    (= movement-type "Dividendo") :dividend
    (= movement-type "Leilão de Fração") :waste
    (= movement-type "Direitos de Subscrição - Exercido") :subscription
    (= movement-type "Bonificação em Ativos") :bonus
    (= movement-type "Resgate") :redemption
    (= movement-type "Grupamento") :reverse-split
    (= movement-type "Desdobro") :split
    (= movement-type "Atualização") :update
    :else (-> (:movement-type mov)
              (string/replace " " "")
              string/lower-case
              keyword)))

(defn mov-date->transaction-created-at
  [date]
  (let [split (string/split date #"/")]
    (Integer/parseInt (str (last split) (second split) (first split)))))

(defn mov-date->keyword-date
  [date]
  (let [split (string/split date #"/")]
    (keyword (str (last split) "-" (second split) "-" (first split)))))

(defn last-brl-historic-price
  [date brl->usd-historic]
  (for [x [0 1 2 3]
        :let [date-keyword (aux.t/subtract-days date x)
              usd-price (get brl->usd-historic date-keyword)]
        :when usd-price]
    usd-price))

(defn brl-price
  [price transaction-date brl->usd-historic]
  (let [price-date (mov-date->keyword-date transaction-date)
        usd-price (first (last-brl-historic-price price-date brl->usd-historic))]
    (if usd-price
      (* usd-price price)
      (log/error (str "[TRANSACTION] Don't find USD price for date " price-date)))))

(defn movement-factor->transaction-factor
  "Ex : '/-2'"
  [factor]
  (when factor
    (let [factor' (clojure.string/split factor #"-")]
      {:operator    (first factor')
       :denominator (-> factor' second bigdec)})))

(s/defn gen-transaction-id
  [{:keys [transaction-date unit-price quantity product exchange] :as movement}]
  (let [operation-type (movement-type->transaction-type movement)
        ticket (a.a/movement-ticket->asset-ticket product)
        exchange' (movement-exchange->transaction-exchange exchange)]
    (-> (str ticket "-" transaction-date "-" unit-price "-" quantity "-" operation-type "-" exchange')
        (string/replace "/" "")
        (string/replace ":" ""))))

(s/defn transaction->id :- s/Uuid
  ([{:transaction/keys [ticket created-at average-price quantity operation-type exchange]}]
   (-> (str ticket "-" created-at "-" average-price "-" quantity "-" operation-type "-" exchange)
       aux.u/string->uuid))
  ([ticket
    created-at
    average-price
    quantity
    exchange
    operation-type]
   (-> (str ticket "-" created-at "-" average-price "-" quantity "-" operation-type "-" exchange)
       aux.u/string->uuid)))

(s/defn movements->transaction :- m.t/Transaction
  [{:keys [transaction-date unit-price quantity exchange product operation-total currency
           incorporated-by factor] :as movement}
   {brl->usd-historic :forex-usd/historic}]
  (let [operation-type (movement-type->transaction-type movement)
        ticket (a.a/movement-ticket->asset-ticket product)
        currency' (if currency (keyword currency) :BRL)
        unit-price-bigdec (safe-number->bigdec unit-price)
        unit-price' (if (= currency' :UST)
                      (brl-price unit-price-bigdec transaction-date brl->usd-historic)
                      unit-price-bigdec)
        operation-total-bigdec (safe-number->bigdec operation-total)
        operation-total' (if (= currency' :UST)
                           (brl-price operation-total-bigdec transaction-date brl->usd-historic)
                           operation-total-bigdec)
        created-at (mov-date->transaction-created-at (str transaction-date))
        average-price (safe-number->bigdec unit-price')
        quantity' (safe-number->bigdec quantity)
        exchange' (movement-exchange->transaction-exchange exchange)]
    ;(println "[TRANSACTION] Row => " movement)
    (aux.u/assoc-if
      {:transaction/id              (transaction->id ticket created-at average-price quantity' exchange' operation-type)
       :transaction/created-at      created-at
       :transaction.asset/ticket    ticket
       :transaction/average-price   average-price
       :transaction/quantity        quantity'
       :transaction/exchange        exchange'
       :transaction/operation-type  operation-type
       :transaction/processed-at    (-> (t/local-date-time) str)
       :transaction/operation-total (safe-number->bigdec operation-total')
       :transaction/currency        currency'}
      :transaction/incorporated-by (a.a/movement-ticket->asset-ticket incorporated-by)
      :transaction/factor          (movement-factor->transaction-factor factor))))

(defn movements->transactions
  [mov forex-usd]
  (log/info "[TRANSACTION] Processing adapter...current transactions [" (count mov) "]")
  (let [mov-transactions (->> mov
                              (map #(movements->transaction % forex-usd))
                              (group-by :transaction/id)
                              (map #(->> % val (sort-by :transaction/processed-at) last)))]
    (log/info "[TRANSACTION] Concluded adapter... "
              "read transactions[" (count mov-transactions) "] "
              "result [" (count mov-transactions) "]")
    mov-transactions))
