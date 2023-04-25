(ns lob-asset-management.turn-b3-movement-into-transaction
  (:require [clojure.test :refer [deftest is]]
            [matcher-combinators.test :refer [match?]] ;; adds support for `match?` and `thrown-match?` in `is` expressions
            [lob-asset-management.fixture.b3 :as f.b3]
            [lob-asset-management.fixture.transaction :as f.t]
            [lob-asset-management.adapter.transaction :as a.t]
            [lob-asset-management.fixture.asset :as f.a]))

(deftest turn-b3-movement-into-transaction-test
  ;"Adapt the B3 movement into a transaction model"
  (is (match? f.t/transaction
              (a.t/movements->transaction f.b3/b3-movement))))