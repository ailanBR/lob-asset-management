(ns lob-asset-management.adapter.portfolio
  (:require [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.adapter.asset :as a.a]))

(defmulti update-quantity (fn [_ _ op] (keyword op)))

(defmethod update-quantity :buy
  [c-qt t-qt _]
  (+ (or c-qt 0.0) t-qt))

(defmethod update-quantity :sell                            ;TODO: throw exception when c-qt = 0
  [c-qt t-qt _]
  (- (or c-qt 0.0) t-qt))

(defmulti updated-total-cost (fn [_ {:transaction/keys [operation-type]}] (keyword operation-type)))

(defmethod updated-total-cost :buy
  [{c-qt :portfolio/quantity
    c-ap :portfolio/average-price}
   {:transaction/keys [quantity average-price]}]
  (let [tt-t (* average-price quantity)
        tt-c (* (or c-qt 0.0) (or c-ap 0.0))]
    (+ tt-t tt-c)))

(defmethod updated-total-cost :sell
  [{c-qt :portfolio/quantity
    c-ap :portfolio/average-price}
   {:transaction/keys [quantity average-price]}]
  (let [tt-t (* average-price quantity)
        tt-c (* (or c-qt 0.0) (or c-ap 0.0))]
    (- tt-t tt-c)))

(defn consolidate-transactions
  [{consolidate-quantity :portfolio/quantity
    transaction-ids :portfolio/transaction-ids :as consolidated}
   {:transaction/keys [id operation-type quantity]
    ticket :transaction.asset/ticket :as transaction}]
  (let [updated-quantity (update-quantity consolidate-quantity quantity operation-type)
        updated-cost (updated-total-cost consolidated transaction)]
    {:portfolio/ticket          ticket
     :portfolio/average-price   (/ updated-cost updated-quantity)
     :portfolio/quantity        updated-quantity
     :portfolio/total-cost      updated-cost
     :portfolio/transaction-ids (conj transaction-ids id)
     :portfolio/category        (-> ticket (a.a/ticket->categories) first)}))

(defn consolidate-grouped-transactions
  ;v1 = ASSET NAME
  ;v2 = TRANSACTIONS
  [[_ v2]]
  (reduce (fn [c t] (consolidate-transactions c t)) {} v2))

(defn filter-operation
  [t]
  (filter (fn [{:transaction/keys [operation-type]}]
            (or (= operation-type :buy)
                (= operation-type :sell)))
          t))

(defn set-portfolio-representation
  ;FIXME : Consider currently value
  [p]
  (let [total-portfolio (reduce #(+ %1 (:portfolio/total-cost %2)) 0M p)]
    (map #(assoc % :portfolio/percentage (* 100 (/ (:portfolio/total-cost %) total-portfolio))) p)))

(defn transactions->portfolio
  [t]
  (->> t
       (filter-operation)                        ;;Accept only buy and sell
       (sort-by :transaction/processed-at)
       (group-by :transaction.asset/ticket)
       (map consolidate-grouped-transactions)
       (set-portfolio-representation)
       (sort-by :portfolio/percentage >)))

(defn consolidate-category
  [{:category/keys [total]}
   {:portfolio/keys [category total-cost]}]
  {:category/name  category
   :category/total-cost (+ (or 0M total) total-cost)})

(defn consolidate-categories
  [[_ p]]
  (reduce consolidate-category {} p))

(defn set-category-representation
  [c]
  (let [total-category (reduce #(+ %1 (:category/total-cost %2)) 0M c)]
    (map #(assoc % :category/percentage (* 100
                                           (/ (:category/total-cost %)
                                              total-category))) c)))

(defn get-category-representation
  [p]
  (->> p
       (group-by :portfolio/category)
       (map consolidate-categories)
       (set-category-representation)))

(comment
  (def t (io.f-in/get-file-by-entity :transaction))

  (def c (transactions->portfolio t))

  (reduce #(+ %1 (:portfolio/percentage %2)) 0M c)

  (clojure.pprint/print-table [:portfolio/ticket :portfolio/category] c)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (get-category-representation c)
  (def category (get-category-representation c))

  (clojure.pprint/print-table category)
  )