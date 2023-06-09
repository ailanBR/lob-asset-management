(ns lob-asset-management.adapter.portfolio
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.logic.portfolio :as l.p]
            [lob-asset-management.aux.util :refer [assoc-if abs]]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.aux.money :refer [safe-big safe-dob]]))

(defmulti update-quantity (fn [_ _ op] (keyword op)))

(defmethod update-quantity :buy
  [c-qt t-qt _]
  (+ (safe-dob c-qt) (abs t-qt)))

(defmethod update-quantity :sell
  [c-qt t-qt _]
  (- (safe-dob c-qt) (abs t-qt)))

(defmulti updated-total-cost (fn [_ {:transaction/keys [operation-type]}] (keyword operation-type)))

(defmethod updated-total-cost :buy
  [{portfolio-quantity :portfolio/quantity
    portfolio-average-price :portfolio/average-price}
   {:transaction/keys [quantity average-price]}]
  (let [tt-t (* average-price quantity)
        tt-c (* (safe-dob portfolio-quantity) (safe-dob portfolio-average-price))]
    (+ tt-t tt-c)))

(defmethod updated-total-cost :sell
  [{portfolio-quantity :portfolio/quantity
    portfolio-average-price :portfolio/average-price}
   {:transaction/keys [quantity average-price]}]
  (let [tt-t (* average-price quantity)
        tt-c (* (safe-dob portfolio-quantity) (safe-dob portfolio-average-price))]
    (- tt-t tt-c)))

(defn consolidate
  [{:portfolio/keys [transaction-ids dividend exchanges status]}
   {:transaction/keys [id exchange]
    ticket :transaction.asset/ticket}
   updated-quantity
   updated-cost]
  (assoc-if {:portfolio/ticket          ticket
             :portfolio/quantity        updated-quantity
             :portfolio/total-cost      updated-cost
             :portfolio/transaction-ids (conj transaction-ids id)
             :portfolio/category        (-> ticket (a.a/ticket->categories) first)
             :portfolio/exchanges       (if (contains? exchanges exchange) exchanges (-> exchanges (conj exchange) set))
             :portfolio/dividend        (safe-big dividend)}
            :portfolio/status status))

(defmulti consolidate-transactions
          (fn [_ {:transaction/keys [operation-type]}]
            (keyword operation-type)))

(defmethod consolidate-transactions :buy
  [{consolidate-quantity :portfolio/quantity
    portfolio-sell-profit :portfolio/sell-profit :as consolidated}
   {:transaction/keys [operation-type quantity] :as transaction}]
  (let [updated-quantity (update-quantity consolidate-quantity quantity operation-type)
        updated-cost (updated-total-cost consolidated transaction)
        consolidated (consolidate consolidated transaction updated-quantity updated-cost)]
    (assoc consolidated
      :portfolio/average-price (/ updated-cost updated-quantity)
      :portfolio/sell-profit (safe-big portfolio-sell-profit))))

;(defn redemption-operation
;  [{consolidate-quantity :portfolio/quantity
;    portfolio-average-price :portfolio/average-price
;    portfolio-sell-profit :portfolio/sell-profit :as consolidated}
;   {:transaction/keys [quantity average-price operation-type] :as transaction}]
;  (let [updated-quantity (update-quantity consolidate-quantity quantity operation-type)
;        updated-cost (updated-total-cost consolidated transaction)
;        consolidated (consolidate consolidated transaction updated-quantity updated-cost)
;        sell-profit (-> average-price (- (safe-big portfolio-average-price)) (* quantity))]
;    (assoc consolidated
;      :portfolio/average-price (or portfolio-average-price average-price)
;      :portfolio/sell-profit   (+ (safe-big portfolio-sell-profit) sell-profit))))

(defmethod consolidate-transactions :sell
  [{consolidate-quantity :portfolio/quantity
    portfolio-average-price :portfolio/average-price
    portfolio-sell-profit :portfolio/sell-profit :as consolidated}
   {:transaction/keys [quantity average-price operation-type] :as transaction}]
  (let [updated-quantity (update-quantity consolidate-quantity quantity operation-type)
        updated-cost (updated-total-cost consolidated transaction)
        consolidated (consolidate consolidated transaction updated-quantity updated-cost)
        sell-profit (-> average-price (- (safe-big portfolio-average-price)) (* quantity))]
    (assoc consolidated
      :portfolio/average-price (or portfolio-average-price average-price)
      :portfolio/sell-profit   (+ (safe-big portfolio-sell-profit) sell-profit))))

(defmethod consolidate-transactions :resgate
  [{:portfolio/keys [transaction-ids exchanges dividend sell-profit status]
    portfolio-average-price :portfolio/average-price}
   {:transaction/keys [quantity id exchange average-price]
    ticket :transaction.asset/ticket}]
  (let [sell-profit (-> average-price
                        (- (safe-big portfolio-average-price))
                        (* quantity)
                        (+ (safe-big sell-profit)))]
    (assoc-if {:portfolio/ticket          ticket
               :portfolio/average-price   0M
               :portfolio/quantity        0.0
               :portfolio/total-cost      0M
               :portfolio/transaction-ids (conj transaction-ids id)
               :portfolio/category        (-> ticket (a.a/ticket->categories) first)
               :portfolio/exchanges       (if (contains? exchanges exchange) exchanges (-> exchanges (conj exchange) set))
               :portfolio/dividend        (safe-big dividend)
               :portfolio/sell-profit     sell-profit}
              :portfolio/status status)))

(defn total-operation
  [{:transaction/keys [quantity average-price operation-total]}]
  (let [calculated-total (* quantity average-price)]
    (if (> 0M calculated-total)
      calculated-total
      operation-total)))

(defn add-dividend-profit
  [{:portfolio/keys [transaction-ids dividend total-cost quantity average-price exchanges
                     sell-profit status]
    portfolio-ticket :portfolio/ticket }
   {:transaction/keys [id exchange] ticket :transaction.asset/ticket :as transaction}]
  (let [transaction-total-operation (total-operation transaction)]
    (assoc-if {:portfolio/ticket          (or portfolio-ticket ticket)
               :portfolio/average-price   (safe-big average-price)
               :portfolio/quantity        (safe-dob quantity)
               :portfolio/total-cost      (safe-big total-cost)
               :portfolio/transaction-ids (conj transaction-ids id)
               :portfolio/category        (-> ticket (a.a/ticket->categories) first)
               :portfolio/exchanges       (if (contains? exchanges exchange) exchanges (-> exchanges (conj exchange) set))
               :portfolio/dividend        (+ (safe-big dividend) transaction-total-operation)
               :portfolio/sell-profit     (safe-big sell-profit)}
              :portfolio/status status)))

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

(defmethod consolidate-transactions :grupamento
  [{:portfolio/keys [ticket average-price transaction-ids exchanges dividend sell-profit status]
    portfolio-quantity :portfolio/quantity}
   {:transaction/keys [quantity id exchange]}]
  (let [factor (/ portfolio-quantity quantity)
        average-price' (* factor average-price)
        total-cost' (* quantity average-price')]
    (assoc-if {:portfolio/ticket          ticket
               :portfolio/average-price   (safe-big average-price')
               :portfolio/quantity        (safe-dob quantity)
               :portfolio/total-cost      (safe-big total-cost')
               :portfolio/transaction-ids (conj transaction-ids id)
               :portfolio/category        (-> ticket (a.a/ticket->categories) first)
               :portfolio/exchanges       (if (contains? exchanges exchange) exchanges (-> exchanges (conj exchange) set))
               :portfolio/dividend        (safe-big dividend)
               :portfolio/sell-profit     (safe-big sell-profit)}
              :portfolio/status status)))

(defn add-transaction-quantity
  [{:portfolio/keys [ticket total-cost transaction-ids exchanges dividend sell-profit status]
    portfolio-quantity :portfolio/quantity}
   {:transaction/keys [quantity id exchange]}]
  (let [quantity' (+ portfolio-quantity quantity)
        average-price' (/ total-cost quantity')]
    (assoc-if {:portfolio/ticket          ticket
               :portfolio/average-price   (safe-big average-price')
               :portfolio/quantity        (safe-dob quantity')
               :portfolio/total-cost      (safe-big total-cost)
               :portfolio/transaction-ids (conj transaction-ids id)
               :portfolio/category        (-> ticket (a.a/ticket->categories) first)
               :portfolio/exchanges       (if (contains? exchanges exchange) exchanges (-> exchanges (conj exchange) set))
               :portfolio/dividend        (safe-big dividend)
               :portfolio/sell-profit     (safe-big sell-profit)}
              :portfolio/status status)))

(defmethod consolidate-transactions :desdobro
  [portfolio transaction]
  (add-transaction-quantity portfolio transaction))

(defmethod consolidate-transactions :bonificaçãoemativos
  [portfolio transaction]
  (add-transaction-quantity portfolio transaction))

(defmethod consolidate-transactions :incorporation
  [{:portfolio/keys [ticket total-cost transaction-ids exchanges dividend sell-profit]
    portfolio-quantity :portfolio/quantity :as portfolio}
   {:transaction/keys [id exchange incorporated-by factor]}]
  (if incorporated-by
    (let [quantity' (condp = (:operator factor)
                      "/" (/ portfolio-quantity (safe-big (:denominator factor)))
                      "*" (* portfolio-quantity (safe-big (:denominator factor))))
          average-price' (/ total-cost quantity')]
      {:portfolio/ticket          incorporated-by
       :portfolio/average-price   average-price'
       :portfolio/quantity        quantity'
       :portfolio/total-cost      (safe-big total-cost)
       :portfolio/transaction-ids (conj transaction-ids id)
       :portfolio/category        (-> ticket (a.a/ticket->categories) first)
       :portfolio/exchanges       (if (contains? exchanges exchange) exchanges (-> exchanges (conj exchange) set))
       :portfolio/dividend        (safe-big dividend)
       :portfolio/sell-profit     (safe-big sell-profit)
       :portfolio/status          :incorporated})
    portfolio))

(defn consolidate-grouped-transactions
  [[_ transactions]]
  (->> transactions
       (sort-by :transaction/created-at)
       (reduce #(consolidate-transactions %1 %2) {})))

(defn filter-operation
  [transaction]
  (filter #(contains?
             #{:buy :sell :JCP :income :dividend :waste :grupamento :desdobro :bonificaçãoemativos :incorporation
               :resgate}
             (:transaction/operation-type %))
          transaction))

(defn remove-fixed-income
  [transaction]
  (remove #(clojure.string/includes?
             (-> % :transaction.asset/ticket name) "CDB")
          transaction))

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
               (not (contains? #{:LINX3 :DEFI11 :USDT :SULA3 :BSEV3 :SULA11} ticket)))
      (log/error (str "[PORTFOLIO] Don't find current value for " ticket)))
    position-value))

(defn set-portfolio-representation
  [assets {usd-last-price :forex-usd/price} portfolio]
  (let [total-portfolio (reduce #(+ %1 (:portfolio/total-cost %2)) 0M portfolio)]
    (when (not usd-last-price) (log/error (str "[PORTFOLIO] Don't find last USD price")))
    (map (fn [{:portfolio/keys [total-cost average-price ticket] :as portfolio-row}]
            ;(format "%.2f" position-value)
           (let [position-value (get-position-value assets usd-last-price portfolio-row)
                 profit-loss (l.p/position-profit-loss-value position-value total-cost)]
             (when (and (> average-price 0M)
                        (<= position-value 0M)
                        (not (contains? #{:SULA3 :BSEV3 :SULA11} ticket)))
               (log/error (str "[PORTFOLIO] Don't find current value for " ticket)))
             (assoc portfolio-row
               :portfolio/total-last-value position-value
               :portfolio/percentage (l.p/position-percentage total-portfolio position-value)
               :portfolio.profit-loss/value profit-loss
               :portfolio.profit-loss/percentage (l.p/position-profit-loss-percentage total-cost profit-loss)))) portfolio)))

(defn transactions->portfolio
  [transactions assets forex-usd]
  (log/info "[PORTFOLIO] Processing adapter...")
  (let [portfolio (->> transactions
                       (filter-operation)                   ;;Accept only buy and sell
                       (remove-fixed-income)
                       (sort-by :transaction/created-at)
                       (group-by :transaction.asset/ticket)
                       (map consolidate-grouped-transactions)
                       (set-portfolio-representation assets forex-usd)
                       (sort-by :portfolio/percentage >))]
    (log/info "[PORTFOLIO] Concluded adapter...[" (count portfolio) "]")
    portfolio))

(defn update-portfolio
  [portfolio assets forex-usd]
  (log/info "[PORTFOLIO] Updating...")
  (let [updated-portfolio (->> portfolio
                               (set-portfolio-representation assets forex-usd)
                               (sort-by :portfolio/percentage >))]
    (log/info "[PORTFOLIO] updated")
    updated-portfolio))

(defn consolidate-category
  [{cat-total-cost :category/total-cost
    cat-last-value :category/total-last-value}
   {:portfolio/keys [category total-cost total-last-value]}]
  (let [position-cost (safe-big total-cost)
        position-last-value (or total-last-value position-cost)
        category-total-cost (safe-big cat-total-cost)
        category-last-value (safe-big cat-last-value)]
    {:category/name             category
     :category/total-cost       (+ category-total-cost position-cost)
     :category/total-last-value (+ category-last-value position-last-value)}))

(defn consolidate-categories
  [[_ p]]
  (reduce consolidate-category {} p))

(defn set-category-representation
  [c]
  (let [total-category (reduce #(+ %1 (or (:category/total-last-value %2)
                                          (:category/total-cost %2))) 0M c)]
    (map #(assoc %
            :category/percentage (l.p/position-percentage total-category
                                                          (or (:category/total-last-value %)
                                                              (:category/total-cost %)))
            :category/profit-loss (- (:category/total-last-value %) (:category/total-cost %))) c)))

(defn get-category-representation
  [portfolio]
  (->> portfolio
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

(defn get-total
  [portfolio]
  (reduce (fn [{:total/keys [profit-asset-grow invested current profit-dividend]}
               {:portfolio/keys [dividend total-cost total-last-value]
                profit-loss :portfolio.profit-loss/value}]
            (let [profit-asset-grow' (+ (safe-big profit-asset-grow) (safe-big profit-loss))
                  profit-dividend' (+ (safe-big profit-dividend) (safe-big dividend))
                  profit-total (+ profit-asset-grow' profit-dividend')
                  total-invested (+ (safe-big invested) (safe-big total-cost))
                  total-current (+ (safe-big current) (safe-big total-last-value))]
              {:total/profit-asset-grow profit-asset-grow'
               :total/profit-dividend   profit-dividend'
               :total/profit-total      profit-total
               :total/profit-total-percentage (l.p/position-profit-loss-percentage total-invested profit-total)
               :total/invested          total-invested
               :total/current           total-current})) {} portfolio))

(comment
  (def t (lob-asset-management.io.file-in/get-file-by-entity :transaction))
  (def a (lob-asset-management.io.file-in/get-file-by-entity :asset))
  (def f (lob-asset-management.io.file-in/get-file-by-entity :forex-usd))
  (transactions->portfolio t a f)
  ;===============================================
  (def c (transactions->portfolio t a f))
  (reduce #(+ %1 (:portfolio/percentage %2)) 0M c)
  (clojure.pprint/print-table [:portfolio/ticket :portfolio/total-cost :portfolio/total-last-value :portfolio/quantity :portfolio.profit-loss/percentage :portfolio.profit-loss/value] c)
  (clojure.pprint/print-table [:portfolio/ticket :portfolio/sell-profit] c)
  ;===============================================
  ;Category FLOW
  (get-category-representation c)
  (def category (get-category-representation c))
  (filter #(= nil (:portfolio/exchange %)) c)
  (clojure.pprint/print-table category)
  ;===============================================
  ;TOTAL profit/loss + dividend
  (reduce #(+ %1 (:portfolio.profit-loss/value %2) (:portfolio/dividend %2)) 0M c)
  (reduce #(+ %1 (:portfolio/dividend %2)) 0M c)
  ;===============================================
  (let [forex-usd (lob-asset-management.io.file-in/get-file-by-entity :forex-usd)
        assets (lob-asset-management.io.file-in/get-file-by-entity :asset)
        portfolio (lob-asset-management.io.file-in/get-file-by-entity :portfolio)]
    (update-portfolio portfolio assets forex-usd))
  ;===============================================
  (def assets (lob-asset-management.io.file-in/get-file-by-entity :asset))
  (def asset (first assets))

  (defn past-price-date
    [price-date historic to-subtract]
    (let [subtracted-date (aux.t/subtract-days price-date to-subtract)]
      (when (subtracted-date historic)
        {:past-date  subtracted-date
         :past-price (subtracted-date historic)})))

  (let [{:asset.market-price/keys [price price-date historic]
         :asset/keys [type]} (first assets)
        to-subtract 1
        ;day-of-week (-> price-date
        ;                (aux.t/subtract-days to-subtract)
        ;                aux.t/day-of-week)
        ;subtract-days (if (= type :crypto)
        ;                to-subtract
        ;                (condp = day-of-week
        ;                  7 (+ to-subtract 2)
        ;                  6 (+ to-subtract 1)
        ;                  to-subtract))
        ;d1-dt (aux.t/subtract-days price-date subtract-days)
        ;d1-price (d1-dt historic)
        {:keys [past-date past-price]} (first (for [x [0 1 2 3 4]
                                                    :let [subtracted-date (aux.t/subtract-days price-date (+ x to-subtract))
                                                          past-price (subtracted-date historic)]
                                                    :when past-price]
                                                {:past-date  subtracted-date
                                                 :past-price past-price}))
        ;d1-price (or (past-price-date price-date historic to-subtract)
        ;             (past-price-date price-date historic (+ 1 to-subtract))
        ;             (past-price-date price-date historic (+ 2 to-subtract))
        ;             (past-price-date price-date historic (+ 3 to-subtract))
        ;             (past-price-date price-date historic (+ 4 to-subtract)))
        ;d1-dt (aux.t/subtract-days price-date 1)
        diff-amount (- price past-price)
        diff-percentage (if (and (> price 0M) (not= diff-amount 0M))
                          (* 100 (with-precision 4 (/ diff-amount price)))
                          0.0)]
    {:last-price price
     :last-price-date price-date
     :diff-amount diff-amount
     :diff-percentage diff-percentage})

  (def usd-price (lob-asset-management.io.file-in/get-file-by-entity :forex-usd))

  (def usd-last-price (:forex-usd/price usd-price))

  (def portfolio (lob-asset-management.io.file-in/get-file-by-entity :portfolio))
  (def portfolio-row (first portfolio))
  (def p-v (get-position-value assets usd-last-price portfolio-row))
  )