(ns lob-asset-management.controller.metric
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]))

(defn add-api-call
  [endpoint]
  (let [{:metric/keys [api-call] :as metric} (io.f-in/get-file-by-entity :metric)
        api-call' (->> {:endpoint endpoint
                        :when     (aux.t/current-datetime->str)}
                       list
                       (concat (or api-call []))
                       (assoc metric :metric/api-call))
        ]
    (try
      (io.f-out/metric-file api-call')
      (catch Exception e
        (log/error "[METRICS] Error when writing metric information")))))

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
