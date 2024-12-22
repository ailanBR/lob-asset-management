(ns lob-asset-management.fixture.transaction
  (:require [clojure.test :refer :all]
            [lob-asset-management.adapter.transaction :as a.transaction]))

(def transaction
  {:transaction/id             (a.transaction/transaction->id :BBAS3 20220630 0.25M 26.0M :nu :JCP)
   :transaction/created-at     20220630
   :transaction.asset/ticket   :BBAS3
   :transaction/average-price  0.25M
   :transaction/quantity       26.0M
   :transaction/exchange       :nu
   :transaction/operation-type :JCP})
