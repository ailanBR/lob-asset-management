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

(defmulti consolidate-transactions
           (fn [_ {:transaction/keys [operation-type]}]
             (keyword operation-type)))

(defmethod consolidate-transactions :buy
  [{:portfolio/keys [transaction-ids dividend]
    consolidate-quantity :portfolio/quantity :as consolidated}
   {:transaction/keys [id operation-type quantity]
    ticket :transaction.asset/ticket :as transaction}]
  (let [updated-quantity (update-quantity consolidate-quantity quantity operation-type)
        updated-cost (updated-total-cost consolidated transaction)]
    {:portfolio/ticket          ticket
     :portfolio/average-price   (/ updated-cost updated-quantity)
     :portfolio/quantity        updated-quantity
     :portfolio/total-cost      updated-cost
     :portfolio/transaction-ids (conj transaction-ids id)
     :portfolio/category        (-> ticket (a.a/ticket->categories) first)
     :portfolio/dividend        (or dividend 0M)}))

(defmethod consolidate-transactions :sell
  [{:portfolio/keys         [transaction-ids dividend]
    consolidate-quantity :portfolio/quantity
    portfolio-average-price :portfolio/average-price :as consolidated}
   {:transaction/keys [id operation-type quantity]
    ticket            :transaction.asset/ticket :as transaction}]
  (let [updated-quantity (update-quantity consolidate-quantity quantity operation-type)
        updated-cost (updated-total-cost consolidated transaction)]
    {:portfolio/ticket          ticket
     :portfolio/average-price   portfolio-average-price
     :portfolio/quantity        updated-quantity
     :portfolio/total-cost      updated-cost
     :portfolio/transaction-ids (conj transaction-ids id)
     :portfolio/dividend        (or dividend 0M)}))

(defn get-total-operation
  [{:transaction/keys [quantity average-price operation-total]}]
  (let [calculated-total (* quantity average-price)]
    (if (> 0M calculated-total)
      calculated-total
      operation-total)))

(defn add-dividend-profit
  [{:portfolio/keys [transaction-ids dividend total-cost quantity average-price]}
   {:transaction/keys [id] ticket :transaction.asset/ticket :as transaction}]
  (let [transaction-total-operation (get-total-operation transaction)]
    {:portfolio/ticket          ticket
     :portfolio/average-price   (or average-price 0M)
     :portfolio/quantity        (or quantity 0.0)
     :portfolio/total-cost      (or total-cost 0M)
     :portfolio/transaction-ids (conj transaction-ids id)
     :portfolio/category        (-> ticket (a.a/ticket->categories) first)
     :portfolio/dividend        (+ (or dividend 0M) transaction-total-operation)}))

(defmethod consolidate-transactions :JCP
  [consolidated transaction]
  (add-dividend-profit consolidated transaction))

(defmethod consolidate-transactions :income
  [consolidated transaction]
  (add-dividend-profit consolidated transaction))

(defmethod consolidate-transactions :dividend
  [consolidated transaction]
  (add-dividend-profit consolidated transaction))

(defmethod consolidate-transactions :waste
  [consolidated transaction]
  (add-dividend-profit consolidated transaction))

(defn consolidate-grouped-transactions
  [[_ v2]]
  (reduce (fn [c t] (consolidate-transactions c t)) {} v2))

(defn filter-operation
  [t]
  (filter (fn [{:transaction/keys [operation-type]}]
            (contains? #{:buy :sell :JCP :income :dividend :waste} operation-type))
          t))

(defn set-portfolio-representation
  ;FIXME : Consider currently value
  [p]
  (let [total-portfolio (reduce #(+ %1 (:portfolio/total-cost %2)) 0M p)]
    (map #(assoc %
            :portfolio/percentage (if (and (> 0M total-portfolio) (> 0M (:portfolio/total-cost %)))
                                    (* 100 (/ (:portfolio/total-cost %) total-portfolio))
                                    0.0)) p)))

(defn transactions->portfolio
  [t]
  (println "[PORTFOLIO] Processing adapter...")
  (let [portfolio (->> t
                       (filter-operation)                   ;;Accept only buy and sell
                       (sort-by :transaction/created-at)
                       (group-by :transaction.asset/ticket)
                       (map consolidate-grouped-transactions)
                       (set-portfolio-representation)
                       (sort-by :portfolio/percentage >))]
    (println "[PORTFOLIO] Concluded adapter...[" (count portfolio) "]")
    portfolio))

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

(defn portfolio-row->irpf-release
  [{:portfolio/keys [ticket average-price quantity total-cost]}]
  {:ticket  ticket
   :average-price average-price
   :quantity quantity
   :total-cost total-cost})

(defn portfolio-list->irpf-release
  [portfolio-list]
  (map portfolio-row->irpf-release portfolio-list))

(comment
  (def t (io.f-in/get-file-by-entity :transaction))
  (transactions->portfolio t)
  (def c (transactions->portfolio t))

  (reduce #(+ %1 (:portfolio/percentage %2)) 0M c)

  (clojure.pprint/print-table [:portfolio/ticket :portfolio/category] c)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (get-category-representation c)
  (def category (get-category-representation c))

  (clojure.pprint/print-table category)
  )