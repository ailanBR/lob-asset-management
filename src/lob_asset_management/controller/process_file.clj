(ns lob-asset-management.controller.process-file
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.adapter.transaction :as a.t]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.controller.portfolio :as c.p]
            [lob-asset-management.db.asset :as db.a]
            [lob-asset-management.db.forex :as db.f]
            [lob-asset-management.db.transaction :as db.t]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.models.file :as m.f]
            [lob-asset-management.relevant :refer [config incorporation-movements]]))

(defn process-assets
  [movements]
  (log/info "[PROCESS ASSETS] Started")
  (let [db-assets (db.a/get-all)
        assets (a.a/movements->assets movements db-assets)]
    (db.a/upsert-bulk! assets)))

(defn process-transactions
  [movements]
  (log/info "[PROCESS TRANSACTIONS] Started")
  (let [usd-price (db.f/get-all)
        transactions (a.t/movements->transactions movements usd-price)]
    (db.t/upsert-bulk! transactions)))

(defn process-movement
  [movements]
  (let [incorporation (incorporation-movements)
        movements' (concat (or incorporation) movements)
        assets (process-assets movements')
        transactions (process-transactions movements')
        portfolio (c.p/process-transaction transactions {:assets assets
                                                         :db-update true})]
    ;movements'
    (log/info (str "Updates\n"
                   (count assets) " - Assets\n"
                   (count transactions) " - Transactions\n"
                   (count portfolio) " - Portfolio\n"))))

(defn process-folder
  [{:keys [release-folder] :as config-folder}]
  (let [files (io.f-in/get-folder-files release-folder)]
    (->> files
         (map #(io.f-in/read-xlsx-by-file-path % config-folder))
         (apply concat))))

(defn process-folders
  []
  (when-let [movements (->> (:releases config)
                            (map #(-> % first val process-folder))
                            (apply concat))]
    (process-movement movements)))

#_(defn process-b3-folder
  []
  (when-let [folder-files (io.f-in/get-folder-files)]
    (let [b3-movements (->> folder-files
                            (map io.f-in/read-xlsx-by-file-path )
                            (apply concat))]
      (process-movement b3-movements))))

#_(defn process-b3-folder-only-new ;DEPRECATED
  ;Use process-b3-folder the processing is idempotent
  []
  (let [already-read (or (-> :read-release (io.f-in/get-file-by-entity) :read-release) [])
        folder-files (io.f-in/get-folder-files)
        new-files (->> folder-files
                       (filter #(not (contains? (set already-read) %))))]
    (when (not (empty? new-files))
      (let [new-movements (->> new-files
                               (map io.f-in/read-xlsx-by-file-path)
                               (apply concat))
            process-movements (process-movement new-movements)]
        (println process-movements)

        (io.f-out/upsert {:read-release (->> new-files
                                             (conj already-read)
                                             (apply concat)
                                             (to-array))})))))

(defn backup-cleanup
  [file-keyword]
  (let [file-name (name file-keyword)
        backup-path (io.f-out/backup-folder file-name)
        files (io.f-in/get-folder-files backup-path)]
    (when (and (not (nil? files))
               (not (empty? files)))
      (let [files-timestamp (map #(let [created-timestamp (-> %
                                                              (clojure.string/split #"/")
                                                              last
                                                              (clojure.string/split #"_")
                                                              last
                                                              (clojure.string/split #".edn")
                                                              first
                                                              aux.t/get-millis)]
                                    {:path %
                                     :created-timestamp created-timestamp})
                                 files)
            files-to-delete (filter #(aux.t/less-updated-than-target? 10 (:created-timestamp %))
                                    files-timestamp)]
        (when (and files-to-delete (not (empty? files-to-delete)))
          (map #(io.f-out/delete-file (:path %)) files-to-delete))))))

(defn delete-all-files
  []
  (log/info "DELETING..." )
  (map io.f-in/delete-file m.f/list-file-name))

(comment

  (delete-all-files)

  (process-folders)

  (def cm (process-folders))

  (let [t (->> (db.t/get-all)
               ;(filter #(= :BBAS3 (:transaction.asset/ticket %)))

               )
        p (lob-asset-management.controller.portfolio/process-transaction (db.t/get-all))
        ;(process-portfolio t)
        ]
    (clojure.pprint/pprint p)
    )

  )
