(ns lob-asset-management.logic.asset)

(defn get-asset-by-name
  [assets name]
  (->> assets
       (filter #(= name (:asset/name %)))
       first))

(defn already-exist?
  [ticket db-data]
  (if (empty? db-data)
    false
    (let [db-data-tickets (->> db-data (map :asset/ticket) set)]
      (contains? db-data-tickets ticket))))
