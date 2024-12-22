(ns lob-asset-management.unit.adapter.google-api
  (:require [clojure.test :refer :all]
            [lob-asset-management.adapter.google-api :as a.google-api]
            [matcher-combinators.test :refer [match?]] ))

(def columns-config {:A :type
                     :B :transaction-date
                     :C :movement-type
                     :D :product
                     :E :exchange
                     :F :quantity
                     :G :unit-price
                     :H :operation-total
                     :I :currency})

(deftest get-by-index-test
  (are [index expected]
    (is (match? expected
                (a.google-api/get-by-index index columns-config)))
    0 :type
    2 :movement-type
    6 :unit-price))

(def api-values-return ["Credito" "27/04/2021" "Transferência - Liquidação" "AAPL" "Sproutfy" "0,12202170" "$155,71" "$19,00" "UST"])

(deftest values->indexed-map-test
  (is (match? {0 "Credito"
               1 "27/04/2021"
               3 "AAPL"
               4 "Sproutfy"
               5 "0,12202170"
               6 "$155,71"
               7 "$19,00"
               8 "UST"}
              (a.google-api/values->indexed-map api-values-return 0 {}))))

(def api-result {"majorDimension" "ROWS",
                 "range" "RANGE!A1:I10",
                 "values" [api-values-return]})

(deftest spread-sheet-out->in-test
  (is (match? [{:type             "Credito"
                :transaction-date "27/04/2021"
                :movement-type    "Transferência - Liquidação"
                :product          "AAPL"
                :exchange         "Sproutfy"
                :quantity         "0,12202170"
                :unit-price       "$155,71"
                :operation-total  "$19,00"
                :currency         "UST"}]
              (a.google-api/spread-sheet-out->in api-result columns-config))))
