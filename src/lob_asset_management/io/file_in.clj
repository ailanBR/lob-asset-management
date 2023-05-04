(ns lob-asset-management.io.file-in
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [dk.ative.docjure.spreadsheet :as xlsx]
            [lob-asset-management.aux.file :as aux.f]
            [lob-asset-management.models.file :as m.f]
            [schema.core :as s]))

(def b3-columns {:A :type                       ;;(Credito Debito)
                 :B :transaction-date
                 :C :movement-type              ;;(Juros Sobre Capital Próprio Rendimento Atualização Incorporação VENCIMENTO Dividendo Transferência - Liquidação Direitos de Subscrição - Não Exercido Leilão de Fração Direito de Subscrição Resgate Fração em Ativos)
                 :D :product
                 :E :exchange
                 :F :quantity
                 :G :unit-price
                 :H :operation-total})

(def b3-sheet "Movimentação")

(def b3-release-folder "./in-data/")

(defn read-xlsx-by-file-path
  [file-path]
  (->> (xlsx/load-workbook file-path)
       (xlsx/select-sheet b3-sheet)
       (xlsx/select-columns b3-columns)
       rest
       (filter #(not (nil? %)))))

(defn read-xlsx-by-file-name
  ([file-name]
   (read-xlsx-by-file-name file-name b3-release-folder))
  ([file-name file-folder]
   (let [file-path (str file-folder file-name)]
     (read-xlsx-by-file-path file-path))))

(def root-directory "./out-data/")

(defn file-full-path [file-name]
  (str root-directory file-name "/" file-name ".edn"))

(defn file->edn [file-path]
  (when (aux.f/file-exists? file-path)
    (with-open [in (io/reader file-path)]
      (edn/read-string (slurp in)))))

(s/defn get-file-by-entity
  [entity :- m.f/file-name]
  (let [entity-full-path (file-full-path (name entity))]
    (file->edn entity-full-path)))

(defn read-b3-folder
  ([]
   (read-b3-folder b3-release-folder))
  ([b3-folder]
   (->> b3-folder
        io/file
        file-seq
        (filter #(.isFile %))
        (mapv str)
        (map read-xlsx-by-file-path)
        (apply concat))))

(defn get-b3-folder-files
  ([]
   (get-b3-folder-files b3-release-folder))
  ([b3-folder]
   (->> b3-folder
        io/file
        file-seq
        (filter #(.isFile %))
        (mapv str))))

(s/defn delete-file
  [entity :- m.f/file-name]
  (let [full-path (file-full-path (name entity))]
    (if (aux.f/file-exists? full-path)
      (io/delete-file full-path)
      (print "File doesn't exist or has already been deleted"))))

(comment
  (get-b3-folder-files)
  )