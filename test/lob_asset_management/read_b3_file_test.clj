(ns lob-asset-management.read-b3-file-test
  (:require [clojure.test :refer :all]
            [lob-asset-management.io.file-in :as io.file-in]
            [lob-asset-management.fixture.b3 :as f.b3]))

(def xlsx-config {:releases       [{:b3-release {:columns        {:A :type    ;;(Credito Debito)
                                                                  :B :transaction-date
                                                                  :C :movement-type ;;(Juros Sobre Capital Próprio Rendimento Atualização Incorporação VENCIMENTO Dividendo Transferência - Liquidação Direitos de Subscrição - Não Exercido Leilão de Fração Direito de Subscrição Resgate Fração em Ativos)
                                                                  :D :product
                                                                  :E :exchange
                                                                  :F :quantity
                                                                  :G :unit-price
                                                                  :H :operation-total}
                                                 :sheet "Movimentação"
                                                 :release-folder "./in-data-test/b3"}}]})

(deftest turn-b3-xlsx-into-edn
  (testing "The total lines read from the xlsx is equal 6"
    (is (= (-> (io.file-in/read-xlsx-by-file-name f.b3/b3-file f.b3/b3-folder (-> xlsx-config :releases first :b3-release)) count)
           6)))

  (testing "The first asset is BBAS3"
    (is (= (first (io.file-in/read-xlsx-by-file-name f.b3/b3-file f.b3/b3-folder (-> xlsx-config :releases first :b3-release)))
           f.b3/b3-movement))))
