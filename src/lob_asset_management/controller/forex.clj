(ns lob-asset-management.controller.forex
  (:require [clojure.tools.logging :as log]
            [java-time.api :as t]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.db.forex :as db.f]
            [lob-asset-management.io.http_in :as io.http]
            [schema.core :as s]))

(s/defn get-usd-price
  [output-size :- s/Keyword]
  ;(log/info "[get-usd-price] started")
  (if-let [forex-info (io.http/get-forex-brl->usd output-size)]
    forex-info
    (throw (ex-info "[get-usd-price] Something was wrong in get market data"
                    {:causes #{:wrong-forex-data}}))))

(defn less-updated-than-target
  [{:forex-usd/keys [updated-at]} target-hours]
  (or (not updated-at)
      (< updated-at
          (aux.t/get-current-millis
            (t/minus (t/local-date-time) (t/hours target-hours))))))

(defn- update-forex-usd
  ([forex-data]
   (assoc forex-data
     :forex-usd/updated-at (aux.t/get-current-millis
                             (t/plus (t/local-date-time) (t/hours 5)))))
  ([{forex-usd-historic :forex-usd/historic}
    {:keys [price updated-at date historic]}]
   (let [updated-historic (merge forex-usd-historic historic)]
     {:forex-usd/price      price
      :forex-usd/updated-at updated-at
      :forex-usd/price-date date
      :forex-usd/historic   updated-historic})))

(defn update-usd-price
  ([]
   (update-usd-price (db.f/get-all) 3))
  ([forex-usd]
   (update-usd-price forex-usd 3))
  ([forex-usd update-target-hour]
   (if (or (nil? forex-usd) (less-updated-than-target forex-usd update-target-hour))
     (try
       (log/info "[FOREX-UPDATING] Starting get usd price")
       (let [output-size (if forex-usd :compact :full)
             usd-last-price (get-usd-price output-size)
             updated-forex (update-forex-usd forex-usd usd-last-price)]
         (log/info "[FOREX-UPDATING] Success with size " output-size " last price" (:price usd-last-price))
         (db.f/upsert! updated-forex)
         updated-forex)
       (catch Exception e
         (let [causes (-> e ex-data :causes)]
           (when (contains? causes :alpha-api-limit)
             (let [force-updated-forex (update-forex-usd forex-usd)]
               (db.f/upsert! updated-forex))))))
     (log/warn "[FOREX-UPDATING] No usd price to be updated"))))

(comment
  (get-usd-price :compact)
  (update-usd-price (db.f/get-all) 1)
  )
