(ns lob-asset-management.controller.metric
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.db.metric :as db.m]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.io.file-in :as io.f-in]))

(defn add-api-call
  [endpoint]
  (try
    (db.m/upsert! #:metric{:endpoint endpoint
                           :when     (aux.t/current-datetime->str)})
    (catch Exception e
      (log/error "[METRICS] Error when writing metric information " e))))

(defn total-api-calls
  ([]
   (total-api-calls (io.f-in/get-file-by-entity :metric)))
  ([{:metric/keys [api-call]}]
   (let [today-keyword (aux.t/current-date->keyword)
         calls-today (->> api-call
                          (map #(assoc % :when-date (aux.t/clj-date->date-keyword (:when %))))
                          (group-by :when-date)
                          today-keyword
                          count)]
     {:today       calls-today
      :total       (count api-call)
      :daily-limit 500})))
