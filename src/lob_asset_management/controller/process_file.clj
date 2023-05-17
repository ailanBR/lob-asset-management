(ns lob-asset-management.controller.process-file
  (:require [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.adapter.transaction :as a.t]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.models.file :as m.f]
            [lob-asset-management.relevant :refer [configurations]]))

(defn process-assets-new
  [b3-movements]
  (let [db-assets (io.f-in/get-file-by-entity :asset)
        assets (a.a/movements->assets b3-movements db-assets)]
    (when (not= db-assets assets)
      (println "New assets to be registered")
      (io.f-out/upsert assets)
      assets)))

(defn process-folders-new
  []
  (let [folders (:releases  configurations)
        folder-files (io.f-in/get-b3-folder-files )]))

(defn process-assets
  [b3-movements]
  (let [db-assets (io.f-in/get-file-by-entity :asset)
        assets (a.a/movements->assets b3-movements db-assets)]
    (when (not= db-assets assets)
      (println "New assets to be registered")
      (io.f-out/upsert assets)
      assets)))

(defn process-transactions
  [b3-movements]
  (let [db-transactions (io.f-in/get-file-by-entity :transaction)
        transactions (a.t/movements->transactions b3-movements db-transactions)]
    (when (not= db-transactions transactions)
      (println "New transactions to be registered")
      (io.f-out/upsert transactions)
      transactions)))

(defn process-b3-movement
  [b3-movements]
  (let [_ (process-assets b3-movements)
        transactions (process-transactions b3-movements)
        portfolio (when transactions (a.p/transactions->portfolio transactions))]
    (when portfolio
      (println "New portfolio records to be registered")
      (io.f-out/upsert portfolio))))

(defn process-b3-release
  [b3-file]
  (let [b3-movements (io.f-in/read-xlsx-by-file-name b3-file)]
    (process-b3-movement b3-movements)))

(defn process-b3-release-by-path
  [b3-file-path]
  (let [b3-movements (io.f-in/read-xlsx-by-file-path b3-file-path)]
    (process-b3-movement b3-movements)))

(defn process-b3-folder
  []
  (if-let [folder-files (io.f-in/get-b3-folder-files)]
    (let [b3-movements (->> folder-files
                            (map io.f-in/read-xlsx-by-file-path )
                            (apply concat))]
      (process-b3-movement b3-movements))))


(defn process-b3-folder-only-new
  []
  (let [already-read (or (-> :read-release (io.f-in/get-file-by-entity) :read-release) [])
        folder-files (io.f-in/get-b3-folder-files)
        new-files (->> folder-files
                       (filter #(not (contains? (set already-read) %))))]
    (when (not (empty? new-files))
      (let [new-movements (->> new-files
                               (map io.f-in/read-xlsx-by-file-path)
                               (apply concat))
            process-movements (process-b3-movement new-movements)]
        (println process-movements)

        (io.f-out/upsert {:read-release (->> new-files
                                             (conj already-read)
                                             (apply concat)
                                             (to-array))})))))

(defn delete-all-files
  []
  (println "DELETING..." )
  (map io.f-in/delete-file m.f/list-file-name))

(comment
  (process-b3-folder-only-new)

  (delete-all-files)

  )