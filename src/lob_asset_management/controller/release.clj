(ns lob-asset-management.controller.release
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.controller.portfolio :as c.p]
            [lob-asset-management.db.asset :as db.a]
            [lob-asset-management.db.forex :as db.f]
            [lob-asset-management.db.transaction :as db.t]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.aux.time :as aux.t]))

(defn asset->irpf-description
  [quantity asset-name average-price type]
  (let [br-asset-desc (str (str quantity) " ações de " asset-name " adquirida ao preço médio de R$ " (format "%.2f" average-price))
        eua-asset-dec (str (str quantity) " ações de " asset-name " adquirida ao preço médio de USD " (format "%.2f" average-price))
        crypto-asset-dec (str (str quantity) asset-name " na plataforma Binance ao preço médio de R$ " (format "%.2f" average-price))
        fixed-income-dec (str "APLICACAO DE RENDA FIXA " asset-name)]
    (condp = type
      :fixed-income fixed-income-dec
      :stockBR      br-asset-desc
      :bdr          br-asset-desc
      :fii          br-asset-desc
      :etf          br-asset-desc
      :stockEUA     eua-asset-dec
      :crypto       crypto-asset-dec)))

(defn asset-type->group
  [type]
  (condp = type
    :fixed-income "04"
    :stockBR      "03"
    :bdr          "04"
    :fii          "07"
    :etf          "07"
    :stockEUA     "03"
    :crypto       "08"))

(defn asset-type->code
  [type ticket]
  (condp = type
    :fixed-income "02"
    :stockBR      "01"
    :fii          "03"
    :bdr          "04"
    :etf          "09"
    :stockEUA     "09"
    :crypto       (if (= ticket :BTC)
                    "01"
                    "02")))

(defn asset-type->location
  [type]
  (condp = type
    :stockEUA "249 - Estados Unidos"
    :crypto "105 - Brasil"
    "105 - Brasil"))

(defn get-last-year-price
  [year historic]
  (let [price-date (-> year (+ 1) (str "-01-01") keyword)
        {:keys [past-price]} (first (for [x [1 2 3 4]
                                          :let [subtracted-date (aux.t/subtract-days price-date x)
                                                past-price (subtracted-date historic)]
                                          :when past-price]
                                      {:past-date  subtracted-date
                                       :past-price past-price}))]
    (or past-price 0M)))

(defn generate-irpf-release
  [{:keys [ticket average-price quantity]}
   assets
   {brl->usd-historic :forex-usd/historic}
   year]
  (let [{:asset/keys [tax-number type]
         asset-name :asset/name
         historic :asset.market-price/historic} (first (filter #(= (:asset/ticket %) ticket) assets))
        last-year-price (get-last-year-price year historic)
        last-year-usd-price (get-last-year-price year brl->usd-historic)
        year-total-invested (if (= :stockEUA type)
                              (* (* quantity last-year-price) last-year-usd-price)
                              (* quantity last-year-price))]
    {:ticket               (name ticket)
     :tax-number           tax-number
     :year-total-invested  (format "%.2f" year-total-invested)
     :group                (asset-type->group type)
     :code                 (asset-type->code type ticket)
     :location             (asset-type->location type)
     :description          (asset->irpf-description quantity asset-name average-price type)}))

(defn irpf-release
  "Create a portfolio with transaction end/limite date based to IRPF with information :
  - ticket
  - cnpj
  - average-price
  - description
  - last year price
  - price in the end of the year"
  [year]
  (let [transactions (db.t/get-all)
        next-year-first-date (-> year str Integer/parseInt (+ 1) (str "01" "01") Integer/parseInt)
        filtered-transactions (->> transactions
                                   (sort-by :transaction/created-at)
                                   (filter #(< (:transaction/created-at %) next-year-first-date)))
        forex-usd (db.f/get-all)
        assets (db.a/get-all)
        portfolio-release (-> filtered-transactions
                              (c.p/process-transaction {:assets assets
                                                        :forex-usd forex-usd
                                                        :db-update false})
                              (a.p/portfolio-list->irpf-release))
        assets (db.a/get-all)
        forex-usd (db.f/get-all)
        income-tax-release (->> portfolio-release
                                (map #(generate-irpf-release % assets forex-usd year))
                                (sort-by :year-total-invested)
                                (sort-by :code)
                                (sort-by :group))]
    filtered-transactions
    #_(io.f-out/income-tax-file income-tax-release year)
    #_income-tax-release))

(defn past-price-date
  [price-date historic to-subtract]
  ;(println "----" (aux.t/subtract-days price-date to-subtract) "-" ((aux.t/subtract-days price-date to-subtract) historic))
  (let [subtracted-date (aux.t/subtract-days price-date to-subtract)]
    (when (and subtracted-date (not (nil? (subtracted-date historic))))
      {:past-date  subtracted-date
       :past-price (subtracted-date historic)})))

(defn compare-past-price-asset
  [{:asset.market-price/keys [price price-date historic]
    :asset/keys [ticket]}
   days]
  ;(println ticket "-" price-date "-" (count historic))
  (when historic
    (let [to-subtract days
          {:keys [past-date past-price]} (first (for [x (range 0 10)
                                                      :let [past-price (past-price-date price-date historic (+ x to-subtract))]
                                                      :when past-price]
                                                  past-price))]
      (if past-price
        (let [diff-amount (- price past-price)
              diff-percentage (if (and (> price 0M) (not= diff-amount 0M))
                                (* 100 (with-precision 4 (/ diff-amount price)))
                                0.0)]
          {:ticket          ticket
           :last-price      price
           :last-price-date price-date
           :past-date       past-date
           :diff-amount     diff-amount
           :diff-percentage diff-percentage})
        (log/error "Error getting past price [" ticket "]")))))

(defn compare-past-day-price-assets
  ([]
   (compare-past-day-price-assets 1))
  ([days]
   (let [assets (db.a/get-all)]
     (compare-past-day-price-assets assets days)))
  ([assets days]
   (->> assets
        (remove #(contains? #{:GNDI3 :HAPV3 :BIDI11 :SULA3} (:asset/ticket %)))
        (map #(compare-past-price-asset % days))
        (remove nil?)
        (sort-by :diff-percentage >))))

(comment
  (clojure.pprint/print-table (->> (irpf-release 2022) (sort-by :code)))

  (->> (db.t/get-all)
      (sort-by :transaction/created-at))

  (irpf-release 2023)
  (def ir (irpf-release 2023))

  (->> ir
       (filter #(= (:transaction.asset/ticket %) :ETH))
       (clojure.pprint/print-table))
  (defn past-price-date
    [price-date historic to-subtract]
    (let [subtracted-date (aux.t/subtract-days price-date to-subtract)]
      (when (subtracted-date historic)
        {:past-date  subtracted-date
         :past-price (subtracted-date historic)})))

  (let [{:asset.market-price/keys [price price-date historic]
         :asset/keys [type]} (first assets)
        to-subtract 1
        {:keys [past-date past-price]} (first (for [x [0 1 2 3 4]
                                                    :let [subtracted-date (aux.t/subtract-days price-date (+ x to-subtract))
                                                          past-price (subtracted-date historic)]
                                                    :when past-price]
                                                {:past-date  subtracted-date
                                                 :past-price past-price}))
        diff-amount (- price past-price)
        diff-percentage (if (and (> price 0M) (not= diff-amount 0M))
                          (* 100 (with-precision 4 (/ diff-amount price)))
                          0.0)]
    {:last-price price
     :last-price-date price-date
     :diff-amount diff-amount
     :diff-percentage diff-percentage})

  (clojure.pprint/print-table [:ticket :last-price :diff-percentage :last-price-date] (compare-past-day-price-assets 1))

  )
