(ns lob-asset-management.controller.release
  (:require [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.io.file-in :as io.f-in]))

(defn asset->irpf-description
  [quantity asset-name average-price]
  (str (str quantity) " ações de " asset-name " adquirida ao preço médio de R$ " (format "%.2f" average-price)))

(defn asset-type->group
  [type]
  (condp = type
    :stockBR      "03"
    :bdr          "04"
    :fii          "07"
    :etf          "07"
    :stockEUA     "07"))

(defn asset-type->code
  [type]
  (condp = type
    :stockBR      "01"
    :fii          "03"
    :bdr          "04"
    :etf          "09"
    :stockEUA     "09"))


(defn asset-type->location
  [type]
  (if (= type :stockEUA)
    "249 - Estados Unidos"
    "105 - Brasil"))

(defn generate-release
  ;TODO : Adjust last-year-price to
  ; 1. get from API if necessary
  ; 2. get the last year price (Ex. for 2023)
  [{:keys [ticket average-price quantity]} assets year]
  (let [{:asset/keys [tax-number type]
         asset-name :asset/name
         historic :asset.market-price/historic} (first (filter #(= (:asset/ticket %) ticket) assets))
        last-year-price (or ((-> year (str "-12-31") keyword) historic)
                            ((-> year (str "-12-30") keyword) historic)
                            ((-> year (str "-12-29") keyword) historic)
                            ((-> year (str "-12-28") keyword) historic)
                            0M)
        year-total-invested (* quantity last-year-price)]
    {:ticket               (name ticket)
     :tax-number           tax-number
     :year-total-invested  (format "%.2f" year-total-invested)
     :group                (asset-type->group type)
     :code                 (asset-type->code type)
     :location             (asset-type->location type)
     :description          (asset->irpf-description quantity asset-name average-price)}))

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
        assets (io.f-in/get-file-by-entity :asset)]
    (map #(generate-release % assets year) portfolio-release)))

(comment
  (clojure.pprint/print-table (irpf-release 2022))
  (->> (io.f-in/get-file-by-entity :transaction)
      (sort-by :transaction/created-at))
  )
