(ns lob-asset-management.io.file-out
  (:require [clojure.java.io :as io]
            [java-time.api :as t]
            [lob-asset-management.aux.file :as aux.f]
            [schema.core :as s]
            [lob-asset-management.models.file :as m.f]))

(def root-directory "./out-data/")

(s/defn file-full-path [file-keyword :- m.f/file-name]
  (let [file-name (name file-keyword)]
    (str root-directory file-name "/" file-name ".edn")))

(defn create-backup [file-keyword]
  (let [file-name (name file-keyword)
        source-path (file-full-path file-keyword)
        source-file (io/file source-path)
        target-path (str root-directory file-name "/bkp/" file-name  "_" (t/local-date-time)  ".edn")
        target-file (io/file target-path)]
    (when (aux.f/file-exists? source-file)
      (with-open [in (clojure.java.io/input-stream source-file)
                  out (clojure.java.io/output-stream target-file)]
        (clojure.java.io/copy in out)))))

(defn edn->file [data file-path]
  ;TODO Avoid the necessity of a existent folder
  (with-open [out (io/writer file-path)]
    (binding [*out* out]
      (clojure.pprint/pprint  data))))

(defmulti
  ;TODO: create a way to accept map (only one record)
  upsert
  (fn [data]
    (if true
      (-> data first first first namespace keyword)
      (throw (AssertionError. (str "Wrong input in defmulti. Received [" (type data) "] Necessary [clojure.lang.PersistentVector]"))))))

(defmethod upsert :transaction [data]
  ;(create-backup :transaction)
  (let [file-path (file-full-path :transaction)]
    (edn->file data file-path)))

(defmethod upsert :asset [data]
  ;(create-backup :asset)
  (let [file-path (file-full-path :asset)]
    (edn->file data file-path)))

(defmethod upsert :portfolio [data]
  ;(create-backup :portfolio)
  (let [file-path (file-full-path :portfolio)]
    (edn->file data file-path)))

(comment
  (upsert [{:asset/ticket :ABEV4} {:asset/ticket :SULA11}]))