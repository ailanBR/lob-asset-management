(ns lob-asset-management.unit.adapter.transaction
  (:require [clojure.test :refer [deftest is are]]
            [matcher-combinators.test :refer [match?]] ;; adds support for `match?` and `thrown-match?` in `is` expressions
            [lob-asset-management.fixture.b3 :as f.b3]
            [lob-asset-management.fixture.transaction :as f.t]
            [lob-asset-management.fixture.forex :as f.forex]
            [lob-asset-management.adapter.transaction :as a.t]))

(deftest movement-factor->transaction-factor-test
  (are [factor expect]
    (is (= expect
           (a.t/movement-factor->transaction-factor factor)))
    "/-2" {:operator "/" :denominator 2M}
    "*-4" {:operator "*" :denominator 4M}))

(deftest mov-date->transaction-created-at-test
  (are [in-date expect]
    (is (= expect
           (a.t/mov-date->transaction-created-at in-date)))
    "10/10/2010" 20101010
    "01/10/2010" 20101001
    "21/12/2024" 20241221))

(deftest mov-date->keyword-date-test
  (are [in-date expect]
    (is (= expect
           (a.t/mov-date->keyword-date in-date)))
    "10/10/2010" :2010-10-10
    "01/10/2010" :2010-10-01
    "21/12/2024" :2024-12-21))

(deftest foreign-price->brl-test
  (are [currency date expect]
    (is (= expect
           (a.t/foreign-price->brl currency 1M 10M date (:forex-usd/historic f.forex/usd-historic))))
    :BRL "06/08/2020" {:brl-unit-price 1M :brl-operation-total 10M}
    :UST "06/08/2020" {:brl-unit-price 5.33100M :brl-operation-total 53.3100M}
    :UST "18/06/2014" {:brl-unit-price 2.21970M :brl-operation-total 22.1970M}))

(deftest transaction->id-test
  (is (= #uuid"fe305a16-983e-31e8-967e-7189776c3ed8"
         (a.t/transaction->id :BBAS3 20220630 0.25M 26.0M :nu :JCP))))

(deftest movements->transaction-test
  ;"Adapt the B3 movement into a transaction model"
  (is (match? f.t/transaction
              (a.t/movements->transaction f.b3/b3-movement f.forex/usd-historic))))


