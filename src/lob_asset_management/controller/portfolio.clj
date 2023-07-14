(ns lob-asset-management.controller.portfolio
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.logic.portfolio :as l.p]
            [lob-asset-management.db.portfolio :as db.p]))

(defn consolidate-grouped-transactions
  [[_ transactions]]
  (->> transactions
       (sort-by :transaction/created-at)
       (reduce #(a.p/transaction->portfolio %1 %2) {})))

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
  (let [allowed-operations #{:buy :sell :JCP :income :dividend :waste :grupamento
                             :desdobro :bonificaçãoemativos :incorporation :resgate}
        portfolio (->> transactions
                       (l.p/filter-operation allowed-operations)
                       (l.p/remove-fixed-income)
                       (sort-by :transaction/created-at)
                       (group-by :transaction.asset/ticket)
                       (map consolidate-grouped-transactions)
                       (set-portfolio-representation assets forex-usd)
                       (sort-by :portfolio/percentage >))]
    portfolio))

(defn process-transaction
  "Process list of transactions

  Optional parameters :

  :ticket => filter transactions by ticket
  :assets => receive a list of assets
  :db-update => update database record"
  [transactions & args]
  (log/info "[PORTFOLIO] Processing transactions...")
  (if-let [asset-transactions (if (-> args first :ticket)
                                (filter #(= (-> args first :ticket)
                                            (:transaction.asset/ticket %)) transactions)
                                transactions)]
    (let [forex-usd (or (-> args first :forex-usd)
                        (io.f-in/get-file-by-entity :forex-usd))
          assets (or (-> args first :assets)
                     (io.f-in/get-file-by-entity :asset))
          portfolio (transactions->portfolio asset-transactions assets forex-usd)]
      (when (-> args first :db-update)
        (log/info "[PROCESS PORTFOLIO] New portfolio records to be registered")
        (db.p/update! portfolio))
      portfolio)
    (log/warn "[PROCESS PORTFOLIO] No new portfolio records to be registered")))

(defn update-portfolio-representation
  [portfolio forex-usd]
  (let [assets (io.f-in/get-file-by-entity :asset)
        updated-portfolio (->> portfolio
                               (set-portfolio-representation assets forex-usd)
                               (sort-by :portfolio/percentage >))]
    (when (not= updated-portfolio portfolio)
      (log/info "[PROCESS PORTFOLIO] New portfolio info to be registered")
      (db.p/overwrite! updated-portfolio)
      updated-portfolio)))

(defn consolidate-categories
  [[_ p]]
  (reduce a.p/portfolio->category-representation {} p))

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