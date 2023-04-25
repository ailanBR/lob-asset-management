(ns lob-asset-management.io.file-out
  (:require [clojure.java.io :as io]
            [java-time.api :as t]
            [lob-asset-management.aux.file :as aux.f]))

(def root-directory "./out-data/")

(def asset-directory (str root-directory "asset/"))
(def asset-file-name "asset")

(def transaction-directory (str root-directory "transaction/"))
(def transaction-file-name "transaction")

(defn create-backup [file-path file-name]
  (let [source-path (str file-path file-name ".edn")
        source-file (io/file source-path)
        target-path (str file-path "/bkp/" file-name  "_" (t/local-date-time)  ".edn")
        target-file (io/file target-path)]
    (when (aux.f/file-exists? source-file)
      (with-open [in (clojure.java.io/input-stream source-file)
                  out (clojure.java.io/output-stream target-file)]
        (clojure.java.io/copy in out)))))

(defn edn->file [data file-path]
  (with-open [out (io/writer file-path)]
    (binding [*out* out]
      ;(prn data)
      (clojure.pprint/pprint  data)
      )))

(defmulti
  upsert
  "TODO: create a way to accept map's"
  (fn [data]
    (if true
      (-> data first first first namespace keyword)
      (throw (AssertionError. (str "Wrong input in defmulti. Received [" (type data) "] Necessary [clojure.lang.PersistentVector]"))))))

(defmethod upsert :transaction [data]
  ;(create-backup transaction-directory transaction-file-name)
  (let [file-path (str transaction-directory transaction-file-name ".edn")]
    (edn->file data file-path)))

(defmethod upsert :asset [data]
  ;(create-backup asset-directory asset-file-name)
  (let [file-path (str asset-directory asset-file-name ".edn")]
    (edn->file data file-path)))

(comment
  (upsert [{:asset/ticket :ABEV4} {:asset/ticket :SULA11}]))