(ns lob-asset-management.fixture.b3
  (:require [clojure.test :refer :all]))

(def b3-file "test_read.xlsx")

(def b3-folder "./in-data-test/")

(def b3-movement
  {:type             "Credito"
   :transaction-date "30/06/2022"
   :movement-type    "Juros Sobre Capital Próprio"
   :product          "BBAS3 - BANCO DO BRASIL S/A"
   :exchange         "NU INVEST CORRETORA DE VALORES S.A."
   :quantity         26.0
   :unit-price       0.25
   :total-price      5.53})

(def b3-movement-2
  {:type             "Credito"
   :transaction-date "10/05/2022"
   :movement-type    "Transferência - Liquidação"
   :product          "ABEV3 - AMBEV S/A"
   :exchange         "NU INVEST CORRETORA DE VALORES S.A."
   :quantity         1.0
   :unit-price       112.35
   :total-price      112.35})

(def b3-movement-3
  {:type             "Credito"
   :transaction-date "13/05/2022"
   :movement-type    "Transferência - Liquidação"
   :product          "ABEV3 - AMBEV S/A"
   :exchange         "NU INVEST CORRETORA DE VALORES S.A."
   :quantity         2.0
   :unit-price       115.35
   :total-price      215.35})

(def b3-movements [b3-movement b3-movement-2 b3-movement-3])

