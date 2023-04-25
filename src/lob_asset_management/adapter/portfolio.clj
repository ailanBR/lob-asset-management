(ns lob-asset-management.adapter.portfolio
  (:require [lob-asset-management.io.file-in :as io.f-in]))

(defmulti update-quantity (fn [_ _ op] (keyword op)))

(defmethod update-quantity :buy
  [c-qt t-qt _]
  (+ (or c-qt 0.0) t-qt))

(defmethod update-quantity :sell                            ;TODO: throw exception when c-qt = 0
  [c-qt t-qt _]
  (- (or c-qt 0.0) t-qt))

(defmulti updated-total-cost (fn [_ {:transaction/keys [operation-type]}] (keyword operation-type)))

(defmethod updated-total-cost :buy
  [{c-qt :transaction/quantity
    c-ap :transaction/average-price}
   {:transaction/keys [quantity average-price]}]
  (let [tt-t (* average-price quantity)
        tt-c (* (or c-qt 0.0) (or c-ap 0.0))]
    (+ tt-t tt-c)))

(defmethod updated-total-cost :sell
  [{c-qt :transaction/quantity
    c-ap :transaction/average-price}
   {:transaction/keys [quantity average-price]}]
  (let [tt-t (* average-price quantity)
        tt-c (* (or c-qt 0.0) (or c-ap 0.0))]
    (- tt-t tt-c)))

(defn conso
  ;TODO: ? Create a transactions list ?
  [{consolidate-quantity :portfolio/quantity :as consolidated}
   {:transaction/keys [operation-type quantity]
    ticket :transaction.asset/ticket :as transaction}]
  (let [updated-quantity (update-quantity consolidate-quantity quantity operation-type)
        updated-cost (updated-total-cost consolidated transaction)]
    {:portfolio/ticket         ticket
     :portfolio/average-price  (/ updated-cost updated-quantity)
     :portfolio/quantity       updated-quantity
     :portfolio/total-cost     updated-cost}))

(defn consolidate
  ;v1 = ASSET NAME
  ;v2 = TRANSACTIONS
  [[_ v2]]
  (reduce (fn [c t] (conso c t)) {} v2))

(defn filter-operation
  [t]
  (filter (fn [{:transaction/keys [operation-type]}]
            (or (= operation-type :buy)
                (= operation-type :sell)))
          t))

(defn transactions->portfolio
  [t]
  ;TODO
  ; [x]Group by asset
  ; [x]Accept only buy and sell operations
  ; [x]Calculate the average price
  ; []
  ; []?Create a list of transaction instead the consolidation?
  (->> t
       (filter-operation)                        ;;Accept only buy and sell
       (sort-by :transaction/processed-at)
       (group-by :transaction.asset/ticket)
       (map consolidate)
       ;(map consolidate-transaction->portfolio)
       ))

(comment
  (def t (io.f-in/read-transaction))

  (def c (transactions->portfolio t))

  (clojure.pprint/print-table c)

  )