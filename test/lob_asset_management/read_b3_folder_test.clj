(ns lob-asset-management.read-b3-folder-test
  (:require [clojure.test :refer :all]
            [lob-asset-management.io.file-in :as io.file-in]
            [lob-asset-management.fixture.b3 :as f.b3]))

;TODO: think about use Midje to use the same let in multiple tests scenarios and avoid define as global
(def records (io.file-in/read-b3-folder "./in-data-test/"))

(deftest turn-b3-folder-into-edn
  ;[records (io.file-in/read-b3-folder "./in-data-test/")]
  (testing "The total lines read from the xlsx is equal 6"
    (is (= (count records)
           8)))

  (testing "The first asset is BBAS3"
    (is (= (-> records first)
           f.b3/b3-movement))))