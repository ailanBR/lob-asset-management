(ns lob-asset-management.aux.file
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn file-exists? [path]
  (.exists (io/file path)))

(defn file->edn [file-path]
  (when (file-exists? file-path)
    (with-open [in (io/reader file-path)]
      (edn/read-string (slurp in)))))

(defn edn->file [data file-path]
  ;TODO Avoid the necessity of an existent folder
  (with-open [out (io/writer file-path)]
    (binding [*out* out]
      (clojure.pprint/pprint data))))