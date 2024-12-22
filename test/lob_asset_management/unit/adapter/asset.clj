(ns lob-asset-management.unit.adapter.asset
  (:require [clojure.test :refer [deftest is]]
            [matcher-combinators.test :refer [match?]] ;; adds support for `match?` and `thrown-match?` in `is` expressions
            [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.fixture.b3 :as f.b3]
            [lob-asset-management.fixture.asset :as f.a]))

(deftest movements->asset-test
  ;"Adapt the B3 movement into a transaction model"
  (is (match? f.a/asset
              (a.a/movements->asset f.b3/b3-movement))))
