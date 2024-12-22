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

(s/defn movement-type->transaction-type
  [{:keys [movement-type] :as mov}]
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

(s/defn mov-date->transaction-created-at :- s/Int
  [date :- s/Str]
  (let [split (string/split date #"/")]
    (Integer/parseInt (str (last split) (second split) (first split)))))

(s/defn mov-date->keyword-date :- s/Keyword
  [date :- s/Str]
  (let [split (string/split date #"/")]
    (keyword (str (last split) "-" (second split) "-" (first split)))))

(defn last-brl-historic-price
  [date brl->usd-historic]
  (for [x [0 1 2 3]
        :let [date-keyword (aux.t/subtract-days date x)
              usd-price (get brl->usd-historic date-keyword)]
        :when usd-price]
    usd-price))

(comment
  (let [brl->ust {:2024-12-10 1.15610M
                  :2024-12-11 2.21970M
                  :2024-12-12 3.33100M
                  :2024-12-13 4.25320M
                  :2024-12-21 5.21970M
                  :2024-12-22 6.33100M
                  :2024-12-23 7.25320M}
        target :2024-12-24
        target-milliseconds (aux.t/date-keyword->milliseconds target)
        day-diff-milliseconds 86400000]
    (->> (map (fn [[k v]] {(aux.t/date-keyword->milliseconds k) v}) brl->ust)
         (reduce merge)
         (filter (fn [[k v]] (<= k target-milliseconds)))
         last
         ((fn [[k v]]
            (let [diff-target-last-price (- target-milliseconds k)
                  limit (* 3 day-diff-milliseconds)]
              (when (< diff-target-last-price limit) v)))))))

(defn brl-price
  [price transaction-date brl->usd-historic]
  (let [price-date (mov-date->keyword-date transaction-date)
        usd-price (first (last-brl-historic-price price-date brl->usd-historic))]
    (if usd-price
      (* usd-price price)
      (log/error (str "[TRANSACTION] Don't find USD price for date " price-date)))))

(s/defn movement-factor->transaction-factor :- (s/maybe m.t/Factor)
  "Ex : '/-2'"
  [factor :- (s/maybe s/Str)]
  (when factor
    (let [factor' (clojure.string/split factor #"-")]
      {:operator    (first factor')
       :denominator (-> factor' second bigdec)})))

(s/defn transaction->id :- s/Uuid
  ([{:transaction/keys [ticket created-at average-price quantity operation-type exchange]} :- m.t/Transaction]
   (-> (str ticket "-" created-at "-" average-price "-" quantity "-" operation-type "-" exchange)
       aux.u/string->uuid))
  ([ticket :- s/Keyword
    created-at :- s/Int
    average-price :- BigDecimal
    quantity :- BigDecimal
    exchange :- m.t/Exchange
    operation-type :- s/Keyword]
   (-> (str ticket "-" created-at "-" average-price "-" quantity "-" operation-type "-" exchange)
       aux.u/string->uuid)))

(defn foreign-price->brl
  [currency unit-price operation-total transaction-date brl->usd-historic]
  (let [unit-price-bigdec (safe-number->bigdec unit-price)
        operation-total-bigdec (safe-number->bigdec operation-total)
        ust->brl #(brl-price % transaction-date brl->usd-historic)]
    (if (= currency :UST)
      {:brl-unit-price      (ust->brl unit-price-bigdec)
       :brl-operation-total (ust->brl operation-total-bigdec)}
       {:brl-unit-price      unit-price-bigdec
        :brl-operation-total operation-total-bigdec})))

(s/defn movements->transaction :- m.t/Transaction
  [{:keys [transaction-date unit-price quantity exchange product operation-total currency
           incorporated-by factor] :as movement}
   {brl->usd-historic :forex-usd/historic}]
  (let [operation-type (movement-type->transaction-type movement)
        ticket (a.a/movement-ticket->asset-ticket product)
        currency' (if currency (keyword currency) :BRL)
        {:keys [brl-unit-price brl-operation-total]} (foreign-price->brl currency' unit-price operation-total transaction-date brl->usd-historic)
        created-at (mov-date->transaction-created-at (str transaction-date))
        average-price (safe-number->bigdec brl-unit-price)
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
       :transaction/operation-total (safe-number->bigdec brl-operation-total)
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
