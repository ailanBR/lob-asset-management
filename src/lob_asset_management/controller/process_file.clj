(ns lob-asset-management.controller.process-file
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.adapter.transaction :as a.t]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.models.file :as m.f]
            [lob-asset-management.relevant :refer [configurations]]))

(defn process-assets
  [movements]
  (log/info "[PROCESS ASSETS] Started")
  (let [db-assets (io.f-in/get-file-by-entity :asset)
        assets (a.a/movements->assets movements db-assets)]
    (when (not= db-assets assets)
      (log/info "[PROCESS ASSETS] New assets to be registered")
      (io.f-out/upsert assets)
      assets)))

(defn process-transactions
  [movements]
  (log/info "[PROCESS TRANSACTIONS] Started")
  (let [db-transactions (io.f-in/get-file-by-entity :transaction)
        transactions (a.t/movements->transactions movements db-transactions)]
    (when (not= db-transactions transactions)
      (log/info "[PROCESS TRANSACTIONS] New transactions to be registered")
      (io.f-out/upsert transactions)
      transactions)))

(defn process-portfolio
  [transactions]
  (when transactions
    (let [portfolio (a.p/transactions->portfolio transactions)]
      (log/info "[PROCESS PORTFOLIO] New portfolio records to be registered")
      (io.f-out/upsert portfolio)
      portfolio)))

(defn process-movement
  [movements]
  (let [assets (process-assets movements)
        transactions (process-transactions movements)
        portfolio (process-portfolio transactions)]
    (log/info (str "Updates\n"
                   (count assets) " - Assets\n"
                   (count transactions) " - Transactions\n"
                   (count portfolio) " - Portfolio\n")))
  ;(let [_ (process-assets movements)
  ;      transactions (process-transactions movements)
  ;      portfolio (process-portfolio transactions)]
    ;(when portfolio
    ;  (log/info "[PROCESS PORTFOLIO] New portfolio records to be registered")
    ;  (io.f-out/upsert portfolio))
    ;)
)

(defn process-folder
  [{:keys [release-folder] :as config-folder}]
  (let [files (io.f-in/get-b3-folder-files release-folder)]
    (->> files
         (map #(io.f-in/read-xlsx-by-file-path % config-folder))
         (apply concat))))

(defn process-folders
  []
  (when-let [movements (->> (:releases configurations)
                          (map #(-> % first val process-folder))
                          (apply concat))]
    (process-movement movements)))

(defn process-b3-folder
  []
  (when-let [folder-files (io.f-in/get-b3-folder-files)]
    (let [b3-movements (->> folder-files
                            (map io.f-in/read-xlsx-by-file-path )
                            (apply concat))]
      (process-movement b3-movements))))

;(defn process-b3-release ;UNUSED
;  [b3-file]
;  (let [b3-movements (io.f-in/read-xlsx-by-file-name b3-file)]
;    (process-b3-movement b3-movements)))
;
;(defn process-b3-release-by-path ;UNUSED
;  [b3-file-path]
;  (let [b3-movements (io.f-in/read-xlsx-by-file-path b3-file-path)]
;    (process-b3-movement b3-movements)))
;
;(defn process-b3-folder-only-new ;DEPRECATED
;  ;Use process-b3-folder the processing is idempotent
;  []
;  (let [already-read (or (-> :read-release (io.f-in/get-file-by-entity) :read-release) [])
;        folder-files (io.f-in/get-b3-folder-files)
;        new-files (->> folder-files
;                       (filter #(not (contains? (set already-read) %))))]
;    (when (not (empty? new-files))
;      (let [new-movements (->> new-files
;                               (map io.f-in/read-xlsx-by-file-path)
;                               (apply concat))
;            process-movements (process-b3-movement new-movements)]
;        (println process-movements)
;
;        (io.f-out/upsert {:read-release (->> new-files
;                                             (conj already-read)
;                                             (apply concat)
;                                             (to-array))})))))

(defn delete-all-files
  []
  (log/info "DELETING..." )
  (map io.f-in/delete-file m.f/list-file-name))

(comment

  (delete-all-files)

  (process-folders)

  (def cm (process-folders))

  )