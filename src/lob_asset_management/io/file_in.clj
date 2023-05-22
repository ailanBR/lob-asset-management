(ns lob-asset-management.io.file-in
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [dk.ative.docjure.spreadsheet :as xlsx]
            [lob-asset-management.aux.file :as aux.f :refer [file->edn]]
            [lob-asset-management.models.file :as m.f]
            [lob-asset-management.relevant :refer [configurations]]
            [schema.core :as s]))


(defn read-xlsx-by-file-path
  ([file-path]
   (read-xlsx-by-file-path file-path (-> configurations :releases first :b3-release)))
  ([file-path {:keys [sheet columns] :as file-config}]
   ;(println "file-path=>" file-path)
   ;(println "config=>" file-config)
   (->> (xlsx/load-workbook file-path)
        (xlsx/select-sheet sheet)
        (xlsx/select-columns columns)
        rest
        (filter #(and (not (nil? %))
                      (not (nil? (get % (-> columns first val))))
                      (not (nil? (get % (-> columns second val))))))

        )))

(defn read-xlsx-by-file-name
  ([file-name]
   (let [{:keys [release-folder] :as xlsx-config} (-> configurations :releases first :b3-release)]
     (read-xlsx-by-file-name file-name release-folder xlsx-config)))
  ([file-name file-folder xlsx-config]
   (let [file-path (str file-folder file-name)]
     (read-xlsx-by-file-path file-path xlsx-config))))


(defn file-full-path [file-name]
  (let [root-directory (:out-data-path configurations)]
    (str root-directory file-name "/" file-name ".edn")))

(s/defn get-file-by-entity
  [entity :- m.f/file-name]
  (let [entity-full-path (file-full-path (name entity))]
    (file->edn entity-full-path)))

(defn read-b3-folder
  ([]
   (read-b3-folder (-> configurations :releases first :b3-release :release-folder )))
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
   (get-b3-folder-files (-> configurations :releases first :b3-release :release-folder )))
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