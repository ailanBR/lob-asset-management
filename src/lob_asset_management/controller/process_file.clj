(ns lob-asset-management.controller.process-file
  (:require [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.adapter.transaction :as a.t]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.models.file :as m.f]))

(defn process-b3-movement
  [b3-movements]
  (let [db-assets (io.f-in/get-file-by-entity :asset)
        db-transactions (io.f-in/get-file-by-entity :transaction)
        assets (a.a/movements->assets b3-movements db-assets)
        transactions (a.t/movements->transactions b3-movements db-transactions)
        ;(->> b3-movements
        ;                   (map a.t/movements->transaction)
        ;                   (filter #(not (contains? % db-transactions))))
        ; Validate transactions filter before delete this code
        portfolio (a.p/transactions->portfolio transactions)]
    (map io.f-out/upsert [assets transactions portfolio])))

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
  (let [folder-files (io.f-in/get-b3-folder-files)]
    (map #(process-b3-release-by-path %) folder-files)))

(defn process-b3-folder-only-new
  []
  (let [already-read (or (-> :read-release (io.f-in/get-file-by-entity) :read-release) [])
        folder-files (io.f-in/get-b3-folder-files)
        new-files (->> folder-files
                       (filter #(not (contains? (set already-read) %)))
                       to-array)]
    (when (not (empty? new-files))
      (map #(process-b3-release-by-path %) new-files)

      (io.f-out/upsert {:read-release (->>
                                        new-files
                                        (conj already-read)
                                        (apply concat)
                                        (to-array))}))))


(defn delete-all-files
  []
  (println "DELETING..." )
  (map io.f-in/delete-file m.f/list-file-name))

(comment
  (process-b3-folder-only-new)

  (delete-all-files)

  )