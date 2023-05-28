(ns lob-asset-management.controller.release
  (:require [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]))

(defn asset->irpf-description
  [quantity asset-name average-price type]
  (let [br-asset-desc (str (str quantity) " ações de " asset-name " adquirida ao preço médio de R$ " (format "%.2f" average-price))
        eua-asset-dec (str (str quantity) " ações de " asset-name " adquirida ao preço médio de USD " (format "%.2f" average-price))
        crypto-asset-dec (str (str quantity) asset-name " na plataforma Binance")]
    (condp = type
      :stockBR  br-asset-desc
      :bdr      br-asset-desc
      :fii      br-asset-desc
      :etf      br-asset-desc
      :stockEUA eua-asset-dec
      :crypto   crypto-asset-dec)))

(defn asset-type->group
  [type]
  (condp = type
    :stockBR      "03"
    :bdr          "04"
    :fii          "07"
    :etf          "07"
    :stockEUA     "03"
    :crypto       "08"))

(defn asset-type->code
  [type ticket]
  (condp = type
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

(defn get-lest-year-price
  [year historic]
  (or ((-> year (str "-12-31") keyword) historic)
      ((-> year (str "-12-30") keyword) historic)
      ((-> year (str "-12-29") keyword) historic)
      ((-> year (str "-12-28") keyword) historic)
      0M))

(defn generate-release
  ;TODO : Adjust last-year-price to
  ; 1. get from API if necessary
  ; 2. get the last year price (Ex. for 2023)
  [{:keys [ticket average-price quantity]}
   assets
   {brl->usd-historic :forex-usd/historic}
   year]
  (let [{:asset/keys [tax-number type]
         asset-name :asset/name
         historic :asset.market-price/historic} (first (filter #(= (:asset/ticket %) ticket) assets))
        last-year-price (get-lest-year-price year historic)
        last-year-usd-price (get-lest-year-price year brl->usd-historic)
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
  (let [transactions (io.f-in/get-file-by-entity :transaction)
        next-year-first-date (-> year
                                 str
                                 Integer/parseInt
                                 (+ 1)
                                 (str "01" "01")
                                 Integer/parseInt)
        filtered-transactions (->> transactions
                                   (sort-by :transaction/created-at)
                                   (filter #(< (:transaction/created-at %) next-year-first-date)))
        portfolio-release (-> filtered-transactions
                              (a.p/transactions->portfolio)
                              (a.p/portfolio-list->irpf-release))
        assets (io.f-in/get-file-by-entity :asset)
        forex-usd (io.f-in/get-file-by-entity :forex-usd)
        income-tax-release (->> portfolio-release
                                (map #(generate-release % assets forex-usd year))
                                (sort-by :year-total-invested)
                                (sort-by :code)
                                (sort-by :group))]
    ;(io.f-out/income-tax-file income-tax-release year)
    income-tax-release
    ))

(comment
  (clojure.pprint/print-table (->> (irpf-release 2022) (sort-by :code)))

  (->> (io.f-in/get-file-by-entity :transaction)
      (sort-by :transaction/created-at))

  (irpf-release 2022)

  )
