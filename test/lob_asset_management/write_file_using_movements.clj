(ns lob-asset-management.write-file-using-movements
  (:require [clojure.test :refer [deftest is]]
            [matcher-combinators.test :refer [match?]] ;; adds support for `match?` and `thrown-match?` in `is` expressions
            [lob-asset-management.fixture.b3 :as f.b3]
            [lob-asset-management.fixture.transaction :as f.t]
            [lob-asset-management.fixture.asset :as f.a]
            [lob-asset-management.controller.process-file :as c.p]))

(deftest read-b3-movements-result-new-asset-file-test
  ;Read the B3 movements and create a list of unique assets by ticket
  (is (match? f.a/assets
              (c.p/process-b3-movement f.b3/b3-movements))))