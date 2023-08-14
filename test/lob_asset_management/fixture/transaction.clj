(ns lob-asset-management.fixture.transaction
  (:require [clojure.test :refer :all]))

(def transaction
  {:transaction/id             "BBAS3-30062022-0.25-26.0-JCP-nu"
   :transaction/created-at     20220630
   :transaction.asset/ticket   :BBAS3
   :transaction/average-price  0.25M
   :transaction/quantity       26.0M
   :transaction/exchange       :nu
   :transaction/operation-type :JCP})