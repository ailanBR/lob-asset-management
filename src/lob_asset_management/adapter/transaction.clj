(ns lob-asset-management.adapter.transaction
  (:require [clojure.tools.logging :as log]
            [schema.core :as s]
            [clojure.string :as string]
            [lob-asset-management.models.transaction :as m.t]
            [lob-asset-management.logic.asset :as l.a]
            [java-time.api :as t]
            ))

(s/defn movement-exchange->transaction-exchange :- m.t/Exchange
  [ex :- s/Str]
  (condp = ex
    "NU INVEST CORRETORA DE VALORES S.A."                       :nu
    "INTER DTVM LTDA"                                           :inter
    "INTER DISTRIBUIDORA DE TITULOS E VALORES MOBILIARIOS LTDA" :inter
    "Sproutfy"                                                  :sproutfy
    (-> ex string/lower-case keyword)))

(defn sell? [mov]
  (and (= (:type mov) "Debito")
       (= (:movement-type mov) "Transferência - Liquidação")))

(defn buy? [mov]
  (and (= (:type mov) "Credito")
       (= (:movement-type mov) "Transferência - Liquidação")))

(defn movement-type->transaction-type [{:keys [movement-type] :as mov}]
  (cond
    (sell? mov) :sell
    (buy? mov) :buy
    (= movement-type "Juros Sobre Capital Próprio") :JCP
    (= movement-type "Rendimento") :income
    (= movement-type "Dividendo") :dividend
    (= movement-type "Leilão de Fração") :waste
    :else (-> (:movement-type mov)
              (string/replace " " "")
              string/lower-case
              keyword)))

(s/defn gen-transaction-id
  [{:keys [transaction-date unit-price quantity product exchange] :as movement}]
  (let [operation-type (movement-type->transaction-type movement)
        ticket (l.a/movement-ticket->asset-ticket product)
        exchange' (movement-exchange->transaction-exchange exchange)]
    (-> (str ticket "-" transaction-date "-" unit-price "-" quantity "-" operation-type "-" exchange')
        (string/replace "/" "")
        (string/replace ":" ""))))

(defn safe-number->bigdec [num]
  (if (number? num)
    (bigdec num)
    (let [formatted-input (-> num
                              str
                              (string/replace #"\$|R|,|\." "")
                              (string/replace " " "")
                              (string/replace "-" ""))
          ]
      (if (empty? formatted-input)
        0M
        (let [formatted-input-bigdec (bigdec formatted-input)
              number-with-decimal-cases (if (>= formatted-input-bigdec 100M)
                                          (/ formatted-input-bigdec 100)
                                          formatted-input-bigdec)]
          number-with-decimal-cases)))))

(defn convert-date
  [date]
  (let [split (string/split date #"/")]
    (Integer/parseInt (str (last split) (second split) (first split)))))

(s/defn movements->transaction :- m.t/Transaction
  [{:keys [transaction-date unit-price quantity exchange product operation-total] :as movement}]
  (let [operation-type (movement-type->transaction-type movement)
        ticket (l.a/movement-ticket->asset-ticket product)]
    ;(println "[TRANSACTION] Row => " movement)
    {:transaction/id              (gen-transaction-id movement)
     :transaction/created-at      (convert-date (str transaction-date))
     ;:transaction/asset          asset
     ;:transaction/asset-id       (UUID/randomUUID)
     :transaction.asset/ticket    ticket
     :transaction/average-price   (safe-number->bigdec unit-price)
     :transaction/quantity        (safe-number->bigdec quantity)
     :transaction/exchange        (movement-exchange->transaction-exchange exchange)
     :transaction/operation-type  operation-type
     :transaction/processed-at    (-> (t/local-date-time) str)
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
   (log/info "[TRANSACTION] Processing adapter...current transactions [" (count db-data) "]")
   (let [mov-transactions (->> mov
                           (map movements->transaction)
                           (group-by :transaction/id)
                           (map #(->> % val (sort-by :transaction/processed-at) last)))
         new-transactions (->> mov-transactions
                               (filter #(already-read-transaction % db-data))
                               (concat (or db-data []))
                               (sort-by :transaction.asset/ticket))]
     (log/info "[TRANSACTION] Concluded adapter... "
               "read transactions[" (count mov-transactions) "] "
               "result [" (count new-transactions) "]")
     new-transactions)))
