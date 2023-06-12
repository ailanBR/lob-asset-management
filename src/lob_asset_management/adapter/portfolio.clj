(ns lob-asset-management.adapter.portfolio
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.logic.portfolio :as l.p]))

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

(defn consolidate
  [{:portfolio/keys [transaction-ids dividend]
    consolidate-quantity :portfolio/quantity :as consolidated}
   {:transaction/keys [id operation-type quantity]
    ticket :transaction.asset/ticket :as transaction}]
  (let [updated-quantity (update-quantity consolidate-quantity quantity operation-type)
        updated-cost (updated-total-cost consolidated transaction)]
    {:portfolio/ticket          ticket
     :portfolio/quantity        updated-quantity
     :portfolio/total-cost      updated-cost
     :portfolio/transaction-ids (conj transaction-ids id)
     :portfolio/category        (-> ticket (a.a/ticket->categories) first)
     :portfolio/dividend        (or dividend 0M)}))

(defmulti consolidate-transactions
           (fn [_ {:transaction/keys [operation-type]}]
             (keyword operation-type)))

(defmethod consolidate-transactions :buy
  [{consolidate-quantity :portfolio/quantity :as consolidated}
   {:transaction/keys [operation-type quantity] :as transaction}]
  (let [updated-quantity (update-quantity consolidate-quantity quantity operation-type)
        updated-cost (updated-total-cost consolidated transaction)
        consolidated (consolidate consolidated transaction)]
    (assoc consolidated
      :portfolio/average-price (/ updated-cost updated-quantity))))

(defmethod consolidate-transactions :sell
  ;TODO : register profit/loss
  [{portfolio-average-price :portfolio/average-price :as consolidated}
   transaction]
  (let [consolidated (consolidate consolidated transaction)]
    (assoc consolidated
      :portfolio/average-price portfolio-average-price)))

(defn total-operation
  [{:transaction/keys [quantity average-price operation-total]}]
  (let [calculated-total (* quantity average-price)]
    (if (> 0M calculated-total)
      calculated-total
      operation-total)))

(defn add-dividend-profit
  [{:portfolio/keys [transaction-ids dividend total-cost quantity average-price]}
   {:transaction/keys [id] ticket :transaction.asset/ticket :as transaction}]
  (let [transaction-total-operation (total-operation transaction)]
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

(defn remove-fixed-income
  [t]
  (remove (fn [{:transaction.asset/keys [ticket]}]
            (clojure.string/includes? (name ticket) "CDB"))
          t))

(defn get-position-value
  [assets usd-last-price {:portfolio/keys [average-price ticket quantity]}]
  (let [{:asset/keys [type]
         last-price  :asset.market-price/price} (->> assets (filter #(= (:asset/ticket %) ticket)) first)
        position-current-price (if (and (= :stockEUA type)
                                        last-price
                                        usd-last-price)
                                 (* usd-last-price last-price)
                                 last-price)
        position-value (* (or position-current-price average-price) quantity)]
    (when (and (> average-price 0M)
               (not position-current-price)
               (not (contains? #{:LINX3 :DEFI11 :USDT :SULA3 :BSEV3} ticket)))
      (log/error (str "[PORTFOLIO] Don't find current value for " ticket)))
    position-value))

(defn set-portfolio-representation
  [p]
  (let [assets (io.f-in/get-file-by-entity :asset)
        {:forex-usd/keys [price]} (io.f-in/get-file-by-entity :forex-usd)
        total-portfolio (reduce #(+ %1 (:portfolio/total-cost %2)) 0M p)]
    (when (not price) (log/error (str "[PORTFOLIO] Don't find last USD price")))
    (map (fn [{:portfolio/keys [total-cost average-price ticket] :as portfolio-row}]
            ;(format "%.2f" position-value)
           (let [position-value (get-position-value assets price portfolio-row)
                 profit-loss (l.p/position-profit-loss-value position-value total-cost)]
             (when (and (> average-price 0M) (<= position-value 0M) (not (contains? #{:SULA3 :BSEV3} ticket)))
               (log/error (str "[PORTFOLIO] Don't find current value for " ticket)))
             (assoc portfolio-row
               :portfolio/total-last-value position-value
               :portfolio/percentage (l.p/position-percentage total-portfolio position-value)
               :portfolio.profit-loss/value profit-loss
               :portfolio.profit-loss/percentage (l.p/position-profit-loss-percentage total-cost profit-loss)))) p)))

(defn transactions->portfolio
  [t]
  (log/info "[PORTFOLIO] Processing adapter...")
  (let [portfolio (->> t
                       (filter-operation)                   ;;Accept only buy and sell
                       (remove-fixed-income)
                       (sort-by :transaction/created-at)
                       (group-by :transaction.asset/ticket)
                       (map consolidate-grouped-transactions)
                       (set-portfolio-representation)
                       (sort-by :portfolio/percentage >))]
    (log/info "[PORTFOLIO] Concluded adapter...[" (count portfolio) "]")
    portfolio))

(defn update-portfolio
  ([]
   (let [portfolio (io.f-in/get-file-by-entity :portfolio)]
     (update-portfolio portfolio)))
  ([p]
   (log/info "[PORTFOLIO] Updating...")
   (let [updated-portfolio (->> p
                                set-portfolio-representation
                                (sort-by :portfolio/percentage >))]
     (log/info "[PORTFOLIO] updated")
     updated-portfolio)))

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

  (clojure.pprint/print-table [:portfolio/ticket :portfolio/total-cost :portfolio/total-last-value :portfolio/quantity :portfolio.profit-loss/percentage :portfolio.profit-loss/value] c)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (get-category-representation c)
  (def category (get-category-representation c))

  (clojure.pprint/print-table category)

  (reduce #(+ %1 (:portfolio.profit-loss/value %2) (:portfolio/dividend %2)) 0M c)
  (reduce #(+ %1 (:portfolio/dividend %2)) 0M c)

  (update-portfolio)

  )