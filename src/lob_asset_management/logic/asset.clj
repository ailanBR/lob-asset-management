(ns lob-asset-management.logic.asset)

(defn get-asset-by-name
  [assets name]
  (->> assets
       (filter #(= name (:asset/name %)))
       first))

(defn already-exist?
  [ticket db-data]
  ;(println "already-exist?> " ticket)
  ;(println "already-exist?> " (empty? db-data))
  (if (empty? db-data)
    false
    (let [db-data-tickets (->> db-data (map :asset/ticket) set)]
      ;(println "already-exist?> " (contains? db-data-tickets ticket))
      (contains? db-data-tickets ticket))))

(defn updated-historic?                                     ;TODO
  [historic]
  ;- Try to get past dates
  ;- Receive a date that is required to exist in the list
  )
