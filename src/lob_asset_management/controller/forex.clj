(ns lob-asset-management.controller.forex
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.io.http_in :as io.http]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [java-time.api :as t]
            [lob-asset-management.aux.time :as aux.t]
            [schema.core :as s]))

(s/defn get-usd-price
  [output-size :- s/Keyword]
  ;(log/info "[get-usd-price] started")
  (if-let [forex-info (io.http/get-forex-brl->usd output-size)]
    forex-info
    ;(let [last-price (forex-last-price forex-info)]
    ;  last-price)
    (log/error "[get-stock-market-price] Something was wrong in get market data")))

(defn less-updated-than-target
  [forex-usd target-hours]
  (or (not (:forex-usd/updated-at forex-usd))
      (< (:forex-usd/updated-at forex-usd)
          (aux.t/get-current-millis
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
  ([]
   (update-usd-price (io.f-in/get-file-by-entity :forex-usd) 1))
  ([forex-usd]
   (update-usd-price forex-usd 1))
  ([forex-usd update-target-hour]
   (if (or (nil? forex-usd) (less-updated-than-target forex-usd update-target-hour))
     (do (log/info "[FOREX-UPDATING] Starting get usd price")
         (let [output-size (if forex-usd :compact :full)
               usd-last-price (get-usd-price output-size)
               updated-forex (update-forex-usd forex-usd usd-last-price)]
           (log/info "[FOREX-UPDATING] Success with size " output-size " last price" (:price usd-last-price))
           (io.f-out/upsert updated-forex)
           updated-forex))
     (log/warn "[FOREX-UPDATING] No usd price to be updated"))))

(comment
  (get-usd-price :compact)
  )
