(ns lob-asset-management.controller.forex
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.io.http_in :as io.http]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [java-time.api :as t]
            [lob-asset-management.aux.time :as aux.t]
            [schema.core :as s]))

(defn keyword-space->underline [m] ;FIXME: Send to http_in or aux due duplicity with Market info
  (zipmap (map #(keyword (clojure.string/replace (name %) " " "_")) (keys m))
          (vals m)))

(defn formatted-data                                        ;FIXME: Send to http_in or aux due duplicity with Market info
  [response]
  (let [meta-data (keyword-space->underline (:Meta_Data response))
        time-series (keyword-space->underline (:Time_Series_FX_Daily response))]
    {:meta-data   meta-data
     :time-series time-series}))

(defn format-historic-price ;FIXME: Send to http_in or aux due duplicity with Market info
  [price-historic]
  (reduce #(assoc %1 (key %2) (-> %2 val keyword-space->underline :4._close bigdec))
          {}
          price-historic))

(defn forex-info->last-refreshed-dt ;FIXME: Send to http_in or aux due duplicity with Market info
  [formatted-data]
  (when-let [latest-refreshed-dt (-> formatted-data :meta-data :5._Last_Refreshed)]
    (-> latest-refreshed-dt
        (clojure.string/split #" ")
        first
        keyword)))

(defn forex-last-price ;FIXME: Send to http_in or aux due similarities with Market info
  [formatted-data]
  (if (or (not (nil? formatted-data))
          (not (empty? formatted-data)))
    (if-let [latest-refreshed-dt (forex-info->last-refreshed-dt formatted-data)]
      (let [latest-refreshed-price (-> formatted-data
                                       :time-series
                                       latest-refreshed-dt
                                       keyword-space->underline
                                       :4._close
                                       bigdec)
            price-historic (-> formatted-data
                               :time-series
                               format-historic-price)]
        {:price      latest-refreshed-price
         :date       latest-refreshed-dt
         :updated-at (aux.t/get-current-millis)
         :historic   price-historic})
      (throw (ex-info "[forex-last-price] (1) Something was wrong in get market data => formatted-data" formatted-data)))
    (throw (ex-info "[forex-last-price] (2) Something was wrong in get market data => formatted-data" formatted-data))))

(s/defn get-usd-price ;FIXME: Send to http_in or aux due duplicity with Market info
  [output-size :- s/Keyword]
  (if-let [forex-info (io.http/get-forex-brl->usd output-size)]
    (let [formatted-data (formatted-data forex-info)
          last-price (forex-last-price formatted-data)]
      last-price)
    (log/error "[get-stock-market-price] Something was wrong in get market data")))

(defn less-updated-than-target
  [forex-usd target-hours]
  (or (not (:forex-usd/updated-at forex-usd))
      (< (:forex-usd/updated-at forex-usd)
          (aux.t/get-current-millis
            (t/minus (t/local-date-time) (t/hours target-hours))))))

(defn update-forex-usd
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
               usd-last-price (get-usd-price :full)
               updated-forex (update-forex-usd forex-usd usd-last-price)]
           (log/info "[FOREX-UPDATING] Success with size " output-size " last price" (:price usd-last-price))
           (io.f-out/upsert updated-forex)
           updated-forex))
     (log/warn "[FOREX-UPDATING] No usd price to be updated"))))

(defn get-brl->usd-price
  []
  (log/info "[FOREX] Stating get USD price")
  (let [usd-last-price (get-usd-price :compact)]
    (log/info "[FOREX] Success last price " (:price usd-last-price))
    usd-last-price))
