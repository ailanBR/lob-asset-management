(ns lob-asset-management.adapter.alpha-vantage-api
  (:require [cheshire.core :as json]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.aux.util :refer [remove-keyword-parenthesis
                                                   str-space->keyword-underline]]))


(defn market-info->last-refreshed-dt
  [meta-data]
  (when-let [latest-refreshed-dt (or (:3._Last_Refreshed meta-data)
                                     (:5._Last_Refreshed meta-data)
                                     (:6._Last_Refreshed meta-data))]
    (-> latest-refreshed-dt
        (clojure.string/split #" ")
        first
        keyword)))

(defn format-historic-price
  [price-historic]
  (->> price-historic
       (reduce #(let [val->keyword (-> %2 val str-space->keyword-underline remove-keyword-parenthesis)]
                  (assoc %1 (key %2) (bigdec (or (:4._close val->keyword)
                                                 (:4a._close_BRL val->keyword))))) {})
       (into (sorted-map))))

(defn formatted-data
  [{:keys [Meta_Data Time_Series_Daily Time_Series_FX_Daily
           Time_Series_Digital_Currency_Daily]}]
  (let [meta-data (str-space->keyword-underline Meta_Data)
        time-series (str-space->keyword-underline (or Time_Series_Daily
                                                      Time_Series_Digital_Currency_Daily
                                                      Time_Series_FX_Daily))]
    (when-let [latest-refreshed-dt (market-info->last-refreshed-dt meta-data)]
      (let [latest-refreshed-price (-> time-series
                                       latest-refreshed-dt
                                       str-space->keyword-underline
                                       remove-keyword-parenthesis)
            closed-price (bigdec (or (:4._close latest-refreshed-price)
                                     (:4a._close_BRL latest-refreshed-price)))
            price-historic (format-historic-price time-series)]
        {:price      closed-price
         :date       latest-refreshed-dt
         :updated-at (aux.t/get-current-millis)
         :historic   price-historic}))))

(defn response->internal
  [body]
  (-> body
      (json/parse-string true)
      str-space->keyword-underline
      remove-keyword-parenthesis
      formatted-data))

(comment
  (get abev-result ":Meta Data")
  )