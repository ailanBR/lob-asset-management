(ns lob-asset-management.controller.release
  (:require [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.adapter.transaction :as a.t]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.models.file :as m.f]))

(defn asset->irpf-description
  [quantity asset-name average-price]
  (str (str quantity) " ações de " asset-name " adquirida ao preço médio de R$ " (format "%.2f" average-price)))

(defn generate-release
  [{:keys [ticket average-price quantity]} assets]
  (let [{:asset/keys [tax-number]
         asset-name :asset/name} (first (filter #(= (:asset/ticket %) ticket) assets))
        year-last-date-price 1.0M
        last-year-price      1.0M]
    {:ticket               (name ticket)
     :tax-number           tax-number
     :description          (asset->irpf-description quantity asset-name average-price)
     :last-year-price      last-year-price
     :year-last-date-price year-last-date-price}))

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
        ]
    (map #(generate-release % assets) portfolio-release)
    ))

(comment
  (clojure.pprint/print-table (irpf-release 2022))
  (->> (io.f-in/get-file-by-entity :transaction)
      (sort-by :transaction/created-at))


  )
