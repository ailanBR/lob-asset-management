(ns lob-asset-management.controller.portfolio
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.db.forex :as db.f]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.logic.portfolio :as l.p]
            [lob-asset-management.db.asset :as db.a]
            [lob-asset-management.db.portfolio :as db.p]
            [lob-asset-management.db.transaction :as db.t]))

;;TODO : Do all together
;; 1. Identify incorporation event
;; 2. Get new asset transactions
;; 3. Concat with current transactions
;;  3.1 Loop for new until get no more
;; 4. Process
;; [DONE]
;; --- Remove duplicated
;; 1. Group by ticket
;; 2. Get ids
;; 4. Reprocess

(defn formatted-transactions
  [transactions]
  (let [allowed-operations #{:buy :sell :JCP :income :dividend :waste :grupamento
                             :desdobro :bonificaçãoemativos :incorporation :resgate}]
    (->> transactions
         (l.p/filter-operation allowed-operations)
         (l.p/remove-fixed-income)
         (sort-by :transaction/created-at))))

(defn get-related-transactions
  [ticket all-transactions]
  (filter #(= ticket (:transaction.asset/ticket %)) all-transactions))

(defn get-incorporation-events
  [transactions all-transactions]
  (let [incorporation-tickets (->> transactions (map :transaction/incorporated-by) (remove nil?))
        transaction-ids (->> transactions (map :transaction/id) set)
        new-transactions (->> incorporation-tickets
                              (map #(get-related-transactions % all-transactions))
                              (remove #(contains? transaction-ids (:transaction/id %)))
                              first)]
    (if new-transactions
      (let [transaction-ids (->> transactions
                                 (concat new-transactions)
                                 (map :transaction/id)
                                 distinct
                                 (remove nil?))
            new-transactions (->> transaction-ids
                                  (map (fn [id]
                                         (filter #(= id (:transaction/id %)) all-transactions)))
                                  (remove nil?)
                                  (apply concat))]
        new-transactions)
      transactions)))

(defn get-incorporation-events-tree
  [transactions]
  (let [transactions-atom (atom transactions)
        continue (atom true)
        all-transactions (db.t/get-all)]
    (while @continue
      (let [related-transactions (get-incorporation-events @transactions-atom all-transactions)]
        (if (not= (count related-transactions) (count @transactions-atom))
          (reset! transactions-atom related-transactions)
          (reset! continue false))))
    (formatted-transactions @transactions-atom)))

(defn consolidate-grouped-transactions
  [[_ transactions]]
  (let [incorporation (->> transactions (map :transaction/incorporated-by) (remove nil?))
        transactions' (if (empty? incorporation)
                        transactions
                        (get-incorporation-events-tree transactions))]
    (->> transactions'
         (sort-by :transaction/created-at)
         (reduce #(a.p/transaction->portfolio %1 %2) {}))))

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
               (not (contains? #{:LINX3 :DEFI11 :USDT :SULA3 :BSEV3 :SULA11 :S3TE11} ticket)))
      (log/error (str "[PORTFOLIO] Don't find current value for " ticket)))
    position-value))

(defn set-portfolio-representation
  [assets {usd-last-price :forex-usd/price} portfolio]
  (let [total-portfolio (reduce #(+ %1 (:portfolio/total-cost %2)) 0M portfolio)]
    (when (not usd-last-price) (log/error (str "[PORTFOLIO] Don't find last USD price")))
    (map (fn [{:portfolio/keys [total-cost average-price ticket dividend] :as portfolio-row}]
           ;(format "%.2f" position-value)
           (let [position-value (get-position-value assets usd-last-price portfolio-row)
                 profit-loss (l.p/position-profit-loss-value position-value total-cost)
                 profit-loss-with-dividend (+ profit-loss dividend)]
             (when (and (> average-price 0M)
                        (<= position-value 0M)
                        (not (contains? #{:SULA3 :BSEV3 :SULA11} ticket)))
               (log/error (str "[PORTFOLIO] Don't find current value for " ticket)))
             (assoc portfolio-row
               :portfolio/total-last-value position-value
               :portfolio/percentage (l.p/position-percentage total-portfolio position-value)
               :portfolio.profit-loss/value profit-loss-with-dividend
               :portfolio.profit-loss/percentage (l.p/position-profit-loss-percentage total-cost profit-loss-with-dividend)))) portfolio)))

(defn remove-duplicated
  [portfolio]
  (let [dup-treatment (->> portfolio
                           (group-by :portfolio/ticket)
                           (filter #(> (count (second %)) 1))
                           (map (fn [p]
                                  (->> p
                                       second
                                       (remove #(not (= :incorporated (:portfolio/status %))))
                                       first)))) ;TODO: Validate more than 1 incorporated event for the same asset
        ; [Maybe add new field 'last-transaction-at' and get the most updated, considering that the process made before get all incorporation]
        dup-tickets (map :portfolio/ticket dup-treatment)
        portfolio-without-dup (reduce (fn [p ticket]
                                        (remove #(= ticket (:portfolio/ticket %)) p)
                                        )
                                      portfolio dup-tickets)
        portfolio' (concat portfolio-without-dup dup-treatment)]
    portfolio'))

(defn transactions->portfolio
  [transactions assets forex-usd]
  (log/info "[PORTFOLIO] Processing adapter...")
    (->> transactions
         formatted-transactions
         (group-by :transaction.asset/ticket)
         (map consolidate-grouped-transactions)
         remove-duplicated
         (set-portfolio-representation assets forex-usd)
         (sort-by :portfolio/percentage >)))

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
                        (db.f/get-all))
          assets (or (-> args first :assets)
                     (db.a/get-all))
          portfolio (transactions->portfolio asset-transactions assets forex-usd)]
      (when (-> args first :db-update)
        (log/info "[PROCESS PORTFOLIO] New portfolio records to be registered")
        (db.p/update! portfolio))
      portfolio)
    (log/warn "[PROCESS PORTFOLIO] No new portfolio records to be registered")))

(defn update-portfolio-representation
  [portfolio forex-usd]
  (let [assets (db.a/get-all)
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

(comment
  ;Lets test
  ;TODO: Step 2 => Remove duplicated by ticket
  (def transactions (db.t/get-all))
  (process-transaction transactions {:db-update false :ticket :GNDI3}) ;OK
  (process-transaction transactions {:db-update false :ticket :BIDI11}) ;OK
  (process-transaction transactions {:db-update false})

  (def portfolio (db.p/get-all))
  (remove-duplicated portfolio)


  )