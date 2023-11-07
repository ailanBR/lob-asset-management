(ns lob-asset-management.adapter.portfolio
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.logic.portfolio :as l.p]
            [lob-asset-management.aux.util :refer [assoc-if abs]]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.aux.money :refer [safe-big safe-dob safe-number->bigdec]]))

(defmulti update-quantity (fn [_ _ op] (keyword op)))

(defmethod update-quantity :buy
  [c-qt t-qt _]
  (+ (safe-dob c-qt) (abs t-qt)))

(defmethod update-quantity :sell
  [c-qt t-qt _]
  (- (safe-dob c-qt) (abs t-qt)))

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

(defmulti transaction->portfolio
          (fn [_ {:transaction/keys [operation-type]}]
            (keyword operation-type)))

(defmethod transaction->portfolio :buy
  [{portfolio-quantity :portfolio/quantity
    portfolio-sell-profit :portfolio/sell-profit
    portfolio-average-price :portfolio/average-price :as portfolio}
   {:transaction/keys [quantity average-price] :as transaction}]
  (let [updated-quantity (+ (safe-dob portfolio-quantity) (abs quantity))
        updated-cost (l.p/buy-total-cost portfolio-quantity portfolio-average-price average-price quantity)
        consolidated (consolidate portfolio transaction updated-quantity updated-cost)]
    (assoc consolidated
      :portfolio/average-price (/ updated-cost updated-quantity)
      :portfolio/sell-profit (safe-big portfolio-sell-profit))))

(defmethod transaction->portfolio :sell
  [{portfolio-quantity :portfolio/quantity
    portfolio-average-price :portfolio/average-price
    portfolio-sell-profit :portfolio/sell-profit :as portfolio}
   {:transaction/keys [quantity average-price] :as transaction}]
  (let [updated-quantity (- (safe-dob portfolio-quantity) (abs quantity))
        updated-cost (l.p/sell-total-cost portfolio-quantity portfolio-average-price average-price quantity)
        consolidated (consolidate portfolio transaction updated-quantity updated-cost)
        sell-profit (-> average-price (- (safe-big portfolio-average-price)) (* quantity))]
    (assoc consolidated
      :portfolio/average-price (or portfolio-average-price average-price)
      :portfolio/sell-profit   (+ (safe-big portfolio-sell-profit) sell-profit))))

(defmethod transaction->portfolio :resgate
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

(defn add-dividend-profit
  [{:portfolio/keys [transaction-ids dividend total-cost exchanges
                     sell-profit status]
    portfolio-ticket :portfolio/ticket
    portfolio-quantity :portfolio/quantity
    portfolio-average-price :portfolio/average-price}
   {:transaction/keys [id exchange quantity average-price operation-total] ticket :transaction.asset/ticket :as transaction}]
  (let [transaction-total-operation (l.p/total-operation quantity average-price operation-total)]
    (assoc-if {:portfolio/ticket          (or portfolio-ticket ticket)
               :portfolio/average-price   (safe-big portfolio-average-price)
               :portfolio/quantity        (safe-dob portfolio-quantity)
               :portfolio/total-cost      (safe-big total-cost)
               :portfolio/transaction-ids (conj transaction-ids id)
               :portfolio/category        (-> ticket (a.a/ticket->categories) first)
               :portfolio/exchanges       (if (contains? exchanges exchange) exchanges (-> exchanges (conj exchange) set))
               :portfolio/dividend        (+ (safe-big dividend) transaction-total-operation)
               :portfolio/sell-profit     (safe-big sell-profit)}
              :portfolio/status status)))

(defmethod transaction->portfolio :JCP
  [consolidated transaction]
  (add-dividend-profit consolidated transaction))

(defmethod transaction->portfolio :income
  [consolidated transaction]
  (add-dividend-profit consolidated transaction))

(defmethod transaction->portfolio :dividend
  [consolidated transaction]
  (add-dividend-profit consolidated transaction))

(defn get-value-fraction
  [value]
  (let [splited (-> value str (clojure.string/split #"\."))]
    (if (> (count splited) 1)
      (->> splited last (str "0.") safe-number->bigdec)
      0M)))

(defmethod transaction->portfolio :waste
  [{:portfolio/keys [transaction-ids dividend exchanges status]
    portfolio-quantity :portfolio/quantity
    portfolio-average-price :portfolio/average-price
    portfolio-cost :portfolio/total-cost
    portfolio-sell-profit :portfolio/sell-profit :as portfolio}
   {:transaction/keys [average-price operation-total id exchange] ticket :transaction.asset/ticket  :as transaction}]
  (if portfolio-quantity
    (let [quantity' (get-value-fraction portfolio-quantity)
          updated-quantity (- (safe-dob portfolio-quantity) quantity')]
      (assoc-if {:portfolio/ticket          ticket
                 :portfolio/quantity        updated-quantity
                 :portfolio/total-cost      portfolio-cost
                 :portfolio/transaction-ids (conj transaction-ids id)
                 :portfolio/category        (-> ticket (a.a/ticket->categories) first)
                 :portfolio/exchanges       (if (contains? exchanges exchange) exchanges (-> exchanges (conj exchange) set))
                 :portfolio/dividend        (safe-big dividend)
                 :portfolio/average-price   (or portfolio-average-price average-price)
                 :portfolio/sell-profit     (+ (safe-big portfolio-sell-profit) operation-total)}
                :portfolio/status status))
    (do (log/info (str "[TRANSACTION->PORTFOLIO] Invalid quantity for WASTE operation ticket" ticket))
        (add-dividend-profit portfolio transaction))))

(defmethod transaction->portfolio :grupamento
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

(defmethod transaction->portfolio :desdobro
  [portfolio transaction]
  (add-transaction-quantity portfolio transaction))

(defmethod transaction->portfolio :bonificaçãoemativos
  [portfolio transaction]
  (add-transaction-quantity portfolio transaction))

(defmethod transaction->portfolio :incorporation
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

(defn portfolio->category-representation
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
  (def t (lob-asset-management.db.transaction/get-all))
  (def a (lob-asset-management.db.asset/get-all))
  (def f (lob-asset-management.io.file-in/get-file-by-entity :forex-usd))
  (def assets (lob-asset-management.db.asset/get-all))
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

  (def portfolio (lob-asset-management.db.portfolio/get-all))
  (def portfolio-row (first portfolio))
  (def p-v (get-position-value assets usd-last-price portfolio-row))
  )
