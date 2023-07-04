(ns lob-asset-management.controller.metric
  (:require [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]))

(defn add-api-call
  [endpoint]
  (let [{:metric/keys [api-call] :as metric} (io.f-in/get-file-by-entity :metric)
        api-call' (->> {:endpoint endpoint
                        :when     (aux.t/date-time-str)}
                       list
                       (concat (or api-call []))
                       (assoc metric :metric/api-call))
        ]
    (io.f-out/metric-file api-call')))