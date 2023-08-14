(ns lob-asset-management.fixture.forex
  (:require [clojure.test :refer :all]))

(def usd-historic {:forex-usd/price 4.90240M,
                   :forex-usd/updated-at 1691588144672,
                   :forex-usd/price-date :2023-08-09,
                   :forex-usd/historic
                   {:2018-09-04 4.15610M :2014-06-18 2.21970M :2020-08-06 5.33100M :2022-06-30 5.25320M}})