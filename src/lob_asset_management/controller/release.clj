(ns lob-asset-management.controller.release
  (:require [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.adapter.transaction :as a.t]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.models.file :as m.f]))

(defn asset->irpf-description
  [quantity ticket average-price]
  (str (str quantity) " ações de " (name ticket) " a um preço médio de R$ " (str average-price)))

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
        filtered-transactions (-> transactions
                                  (sort-by :transaction/created-at)
                                  (filter #(< (:transaction/created-at %) next-year-first-date)))
        portfolio-release (-> filtered-transactions
                      (a.p/transactions->portfolio)
                      (a.p/portfolio-list->irpf-release))
        assets (io.f-in/get-file-by-entity :asset)
        ]
    (map (fn [{:keys [ticket average-price quantity]} assets]
           (let [{:asset/keys [cnpj]}  (filter #(= ticket (:asset/ticket %)) assets)]
             {:cnpj                 cnpj
              :description          (asset->irpf-description quantity ticket average-price)
              :last-year-price      1.0M
              :year-last-date-price 1.0M})))
    {:cnpj
     :description
     :last-year-price
     :year-last-date-price }

    ))

(comment
  ;1. get transactions
  (def transactions (io.f-in/get-file-by-entity :transaction))
  ;2. filter year transaction
  (->> transactions
      (sort-by :transaction/created-at)
      clojure.pprint/print-table)
  ;3. create portfolio

  ;4. get market price in the last day of the year
  ;

  )
