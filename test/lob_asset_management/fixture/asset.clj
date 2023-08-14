(ns lob-asset-management.fixture.asset
  (:require [clojure.test :refer :all]))

(def asset
  {:asset/name        "BBAS3 - BANCO DO BRASIL S/A"
   :asset/ticket      :BBAS3
   :asset/category    [:finance]
   :asset/type        :stockBR})

(def asset-2
  {:asset/name        "ABEV3 - AMBEV S/A"
   :asset/ticket      :ABEV3
   :asset/category    [:food]
   :asset/last-price  0.0M
   :asset/type        :stockBR})

(def assets [asset asset-2])