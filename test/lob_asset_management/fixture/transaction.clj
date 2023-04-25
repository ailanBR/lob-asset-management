(ns lob-asset-management.fixture.transaction
  (:require [clojure.test :refer :all]
            [lob-asset-management.models.asset :as m.a]
            [lob-asset-management.fixture.asset :as f.a]))

(def transaction
  {:transaction/id             uuid?
   :transaction/created-at     "30/06/2022"
   :transaction/asset-ticket   :BBAS3
   :transaction/average-price    0.25
   :transaction/quantity       26.0
   :transaction/exchange       :NU
   :transaction/operation-type :JCP})