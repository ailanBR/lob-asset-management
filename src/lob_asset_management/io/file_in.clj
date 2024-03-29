(ns lob-asset-management.io.file-in
  (:require [clojure.java.io :as io]
            [dk.ative.docjure.spreadsheet :as xlsx]
            [lob-asset-management.aux.file :as aux.f :refer [file->edn]]
            [lob-asset-management.models.file :as m.f]
            [lob-asset-management.relevant :refer [config]]
            [schema.core :as s]))

(defn read-xlsx-by-file-path
  ([file-path]
   (read-xlsx-by-file-path file-path (-> config :releases first :b3-release)))
  ([file-path {:keys [sheet columns]}]
   (when (aux.f/valid-xlsx-file? file-path)
     (->> (xlsx/load-workbook file-path)
          (xlsx/select-sheet sheet)
          (xlsx/select-columns columns)
          rest
          (filter #(and (not (nil? %))
                        (not (nil? (get % (-> columns first val))))
                        (not (nil? (get % (-> columns second val))))))))))

(defn read-xlsx-by-file-name
  ([file-name]
   (let [{:keys [release-folder] :as xlsx-config} (-> config :releases first :b3-release)]
     (read-xlsx-by-file-name file-name release-folder xlsx-config)))
  ([file-name file-folder xlsx-config]
   (let [file-path (str file-folder file-name)]
     (read-xlsx-by-file-path file-path xlsx-config))))

(defn file-full-path [file-name]
  (let [root-directory (:out-data-path config)]
    (str root-directory file-name "/" file-name ".edn")))

(s/defn get-file-by-entity
  [entity :- m.f/file-name]
  (try
    (let [entity-full-path (file-full-path (name entity))]
      (file->edn entity-full-path))
    (catch Exception e
      (throw (ex-info (format "Error when reading %s file %s" entity e)  {:cause e})))))

(defn read-b3-folder
  ([]
   (read-b3-folder (-> config :releases first :b3-release :release-folder )))
  ([b3-folder]
   (->> b3-folder
        io/file
        file-seq
        (filter #(.isFile %))
        (mapv str)
        (map read-xlsx-by-file-path)
        (apply concat))))

(defn get-folder-files
  ([]
   (get-folder-files (-> config :releases first :b3-release :release-folder )))
  ([folder]
   (->> folder
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
  (get-folder-files)
  )
