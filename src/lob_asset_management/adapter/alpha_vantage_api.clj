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

(defmulti formatted-data
          (fn [{:keys [Meta_Data Information]}]
            (cond
              Meta_Data :alpha-api
              Information :alpha-limit
              :else :real-time-crypto)))

(defmethod formatted-data :alpha-api
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

(defmethod formatted-data :real-time-crypto
  [data]
  (let [price (-> data first second vals first bigdec)
        today-date (aux.t/current-date->keyword)
        historic {today-date price}]
    {:price      price
     :date       today-date
     :updated-at (aux.t/get-current-millis)
     :historic   historic}))

(defmethod formatted-data :alpha-limit
  [_]
  (throw (ex-info "Alpha API limit have reached" {:causes #{:alpha-api-limit}})))

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
