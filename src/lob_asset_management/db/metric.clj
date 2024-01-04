(ns lob-asset-management.db.metric
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.aux.xtdb :refer [db-node] :as aux.xtdb]
            [lob-asset-management.aux.util :refer [string->uuid]]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.logic.asset :as l.a]
            [lob-asset-management.relevant :refer [config]]
            [xtdb.api :as xt]))

(defmulti upsert-bulk! (fn [_] (or (:env config) :dev)))

(defmethod upsert-bulk! :dev
  [metrics]
  (try
    (let [{:metric/keys [api-call] :as metric'} (io.f-in/get-file-by-entity :metric)]
      (->> metrics
           (concat (or api-call []))
           (assoc metric' :metric/api-call)
           (io.f-out/metric-file)))
    (catch Exception e
      (throw (ex-info "METRIC UPSERT ERROR" {:cause e})))))

(defn metric->db-id
  [{:metric/keys [endpoint when] :as metric}]
  (->> when
      (str endpoint)
      (string->uuid)
      (assoc metric :xt/id)))

(defmethod upsert-bulk! :prod
  [api-calls]
  (->> api-calls
       (mapv metric->db-id)
       (aux.xtdb/upsert! db-node))
  (xt/sync db-node))

(defn upsert!
  [api-call]
  (-> api-call list upsert-bulk!))
