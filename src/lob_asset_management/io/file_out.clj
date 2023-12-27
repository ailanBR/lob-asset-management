(ns lob-asset-management.io.file-out
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [java-time.api :as t]
            [lob-asset-management.aux.file :refer [edn->file edn->file-table delete] :as aux.f]
            [schema.core :as s]
            [lob-asset-management.models.file :as m.f]
            [lob-asset-management.relevant :refer [config]])
  (:import (clojure.lang PersistentArrayMap PersistentVector ArraySeq LazySeq)))

(s/defn file-full-path
  [file-keyword :- m.f/file-name]
  (let [file-name (name file-keyword)
        root-directory (:out-data-path config)]
    (str root-directory file-name "/" file-name ".edn")))


(defn backup-folder
  [file-name]
  (str (:out-data-path config) file-name "/bkp/"))

(defn create-backup
  [file-keyword]
  (let [file-name (name file-keyword)
        source-path (file-full-path file-keyword)
        source-file (io/file source-path)
        root-directory' (:out-data-path config)
        target-path (str root-directory' file-name "/bkp/" file-name  "_" (t/local-date-time)  ".edn")
        target-file (io/file target-path)]
    (when (aux.f/file-exists? source-file)
      (with-open [in (clojure.java.io/input-stream source-file)
                  out (clojure.java.io/output-stream target-file)]
        (clojure.java.io/copy in out)))))

(defmulti
  upsert
  (fn [data]
    (cond
      (empty? data)
      (log/error "empty data")

      (instance? PersistentArrayMap data)
      (-> data first first (#(if (nil? (namespace %)) (keyword %) (-> % namespace keyword))))

      (or (instance? PersistentVector data)
          (instance? ArraySeq data)
          (instance? LazySeq data))
      (-> data first first first namespace keyword)

      :else
      (throw (AssertionError. (str "Wrong input in defmulti. Received [" (type data) "] Necessary [clojure.lang.PersistentVector]"))))))

(defmethod upsert :transaction
  [data]
  ;(create-backup :transaction)
  (let [file-path (file-full-path :transaction)]
    (edn->file data file-path)))

(defmethod upsert :asset
  [data]
  (create-backup :asset)
  (let [file-path (file-full-path :asset)]
    (edn->file data file-path)))

(defmethod upsert :asset-news
  [data]
  ;(create-backup :asset-news)
  (let [file-path (file-full-path :asset-news)]
    (edn->file data file-path)))

(defmethod upsert :portfolio
  [data]
  ;(create-backup :portfolio)
  (let [file-path (file-full-path :portfolio)]
    (edn->file data file-path)))

(defmethod upsert :read-release [data]
  ;(create-backup :read-release)
  (let [file-path (file-full-path :read-release)]
    (edn->file data file-path)))

(defmethod upsert :forex-usd [data]
  ;(create-backup :read-release)
  (let [file-path (file-full-path :forex-usd)]
    (edn->file data file-path)))

(defn income-tax-file
  [data year]
  (let [root-directory (:out-data-path config)
        file-path (str root-directory "income-tax/" year "/income-tax.edn")]
    (edn->file-table data file-path)))

(defn metric-file
  [data]
  (let [root-directory (:out-data-path config)
        file-path (str root-directory "metric/metric.edn")]
    (edn->file data file-path)))

(defmethod upsert :telegram [data]
  ;(create-backup :read-release)
  (let [file-path (file-full-path :telegram)]
    (edn->file data file-path)))

(defn delete-file
  [file-path]
  (delete file-path))

(comment
  (def assets [{:asset/ticket :ABEV4} {:asset/ticket :SULA11}])

  (def read-release {:read-release/teste ["teste" "teste-2" "teste-3"]})

  (def asset {:asset/ticket :ABEV4})

  (upsert read-release)
  (upsert asset)
  (print (type assets))

  (print (instance? PersistentArrayMap asset))

  (print (instance? PersistentVector asset))

  (print (namespace read-release))

  (print (-> assets first first first namespace keyword))
  (print (-> asset first first (#(if (nil? (namespace %))
                                          (keyword %)
                                          (-> % namespace keyword)))))
  )
