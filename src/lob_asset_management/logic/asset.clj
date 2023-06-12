(ns lob-asset-management.logic.asset
  (:require [lob-asset-management.aux.time :as aux.t]
            [java-time.api :as t]))

(defn get-asset-by-name
  [assets name]
  (->> assets
       (filter #(= name (:asset/name %)))
       first))

(defn already-exist-asset?
  [ticket db-data]
  (if (empty? db-data)
    false
    (let [db-data-tickets (->> db-data (map :asset/ticket) set)]
      (contains? db-data-tickets ticket))))

(defn less-updated-than-target?
  [target-hours updated-at]
  (or (nil? updated-at)
      (< updated-at
         (aux.t/get-current-millis (t/minus (t/local-date-time) (t/hours target-hours))))))