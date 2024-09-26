(ns lob-asset-management.controller.forex
  (:require [clojure.tools.logging :as log]
            [java-time.api :as t]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.aux.util :refer [log-colors]]
            [lob-asset-management.db.forex :as db.f]
            [lob-asset-management.io.http_in :as io.http]
            [schema.core :as s]))

(s/defn get-usd-price
  [output-size :- s/Keyword]
  (if-let [forex-info (io.http/get-forex-brl->usd output-size)]
    forex-info
    (throw (ex-info "[get-usd-price] Something was wrong in get market data"
                    {:causes #{:wrong-forex-data}}))))

(defn less-updated-than-target
  [{:forex-usd/keys [updated-at]} target-hours]
  (or (not updated-at)
      (< updated-at
          (aux.t/get-millis
            (t/minus (t/local-date-time) (t/hours target-hours))))))

(defn- update-forex-usd
  [{forex-usd-historic :forex-usd/historic}
   {:keys [price updated-at date historic]}]
  (let [updated-historic (merge forex-usd-historic historic)]
    {:forex-usd/price      price
     :forex-usd/updated-at updated-at
     :forex-usd/price-date date
     :forex-usd/historic   updated-historic}))

(defn update-usd-price
  ;TODO: Create Retry process
  ([]
   (update-usd-price (db.f/get-all)))
  ([forex-usd]
   (try
     (log/info (str (:okblue log-colors) "[FOREX-UPDATING] Starting get usd price" (:end log-colors)))
     (let [output-size (if forex-usd :compact :full)
           usd-last-price (get-usd-price output-size)
           updated-forex (update-forex-usd forex-usd usd-last-price)]
       (log/info (str (:okgreen log-colors)
                      "[FOREX-UPDATING] Success with size " output-size " last price"
                      (:price usd-last-price)))
       (db.f/upsert! updated-forex)
       updated-forex)
     (catch Exception e
       (let [causes (-> e ex-data :causes)]
         (when (contains? causes :alpha-api-limit)
           (log/error (str (:fail log-colors) "[ERROR] ALPHA API LIMIT" (:end log-colors)))))))))

(comment
  (def f (get-usd-price :full))
  (clojure.pprint/pprint f)

  (db.f/upsert! f)

  (update-usd-price)
  )
