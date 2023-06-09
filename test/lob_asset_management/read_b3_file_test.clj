(ns lob-asset-management.read-b3-file-test
  (:require [clojure.test :refer :all]
            [lob-asset-management.io.file-in :as io.file-in]
            [lob-asset-management.fixture.b3 :as f.b3]))

(deftest turn-b3-xlsx-into-edn
  (testing "The total lines read from the xlsx is equal 6"
    (is (= (-> (io.file-in/read-xlsx-by-file-name f.b3/b3-file f.b3/b3-folder) count)
           6)))

  (testing "The first asset is BBAS3"
    (is (= (-> (io.file-in/read-xlsx-by-file-name f.b3/b3-file f.b3/b3-folder) first)
           f.b3/b3-movement))))
