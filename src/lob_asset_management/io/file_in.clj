(ns lob-asset-management.io.file-in
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [dk.ative.docjure.spreadsheet :as xlsx]
            [lob-asset-management.aux.file :as aux.f]))

(def b3-columns {:A :type                       ;;(Credito Debito)
                 :B :transaction-date
                 :C :movement-type              ;;(Juros Sobre Capital Próprio Rendimento Atualização Incorporação VENCIMENTO Dividendo Transferência - Liquidação Direitos de Subscrição - Não Exercido Leilão de Fração Direito de Subscrição Resgate Fração em Ativos)
                 :D :product
                 :E :exchange
                 :F :quantity
                 :G :unit-price
                 :H :total-price})

(def b3-sheet "Movimentação")

(def b3-release-folder "./in-data/")

(defn read-xlsx [file-name]
  (let [file-path (str b3-release-folder file-name)]
    (->> (xlsx/load-workbook file-path)
         (xlsx/select-sheet b3-sheet)
         (xlsx/select-columns b3-columns)
         rest
         (filter #(not (nil? %))))))

(def root-directory "./out-data/")

(def asset-directory (str root-directory "asset/"))
(def asset-file-name "asset")

(def transaction-directory (str root-directory "transaction/"))
(def transaction-file-name "transaction")

(defn file->edn [file-path]
  (with-open [in (io/reader file-path)]
    (edn/read-string (slurp in))))

;TODO : change read functions to more generic - 1- move the validation to file->edn
(defn read-asset []
  (let [full-path (str asset-directory asset-file-name ".edn")]
    (when (aux.f/file-exists? full-path)
      (file->edn full-path))))

(defn read-transaction []
  (let [full-path (str transaction-directory transaction-file-name ".edn")]
    (when (aux.f/file-exists? full-path)
      (file->edn full-path))))

(comment

  (read-transaction)

  (read-xlsx "movimentacao-20220101-20220630.xlsx")
  (read-xlsx "test_read.xlsx")

  )