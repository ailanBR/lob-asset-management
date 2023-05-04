(ns lob-asset-management.controller.market
  (:require [lob-asset-management.io.http_in :as io.http]
            [java-time.api :as t]))

(defn keyword-space->underline [m]
  (zipmap (map #(keyword (clojure.string/replace (name %) " " "_")) (keys m))
          (vals m)))
(defn formatted-data
  [response]
  (let [meta-data (keyword-space->underline (:Meta_Data response))
        time-series (keyword-space->underline (:Time_Series_Daily response))]
    {:meta-data   meta-data
     :time-series time-series}))

(defn last-price
  [formatted-data]
  (let [latest-refreshed-dt (-> formatted-data :meta-data :3._Last_Refreshed keyword)
        latest-refreshed-price (-> formatted-data
                                   :time-series
                                   latest-refreshed-dt
                                   keyword-space->underline
                                   :4._close
                                   bigdec)]
    {:price latest-refreshed-price
     :date latest-refreshed-dt
     :updated-at  (str (t/local-date-time))}))


(defn get-b3-market-price
  [asset]
  (let [market-info (io.http/get-daily-adjusted-prices asset)
        formatted-data (formatted-data market-info)
        last-price (last-price formatted-data)]
    last-price))

(comment
  (def aux-market-info (io.http/get-daily-adjusted-prices "ABEV3.SAO"))
  (def market-formated (get-b3-market-price "ABEV3.SAO"))
  (clojure.pprint/pprint market-formated)
  (last-price market-formated)
  )