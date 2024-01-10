(ns lob-asset-management.adapter.crypto-asset
  (:require [cheshire.core :as json]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.aux.util :refer [remove-keyword-parenthesis
                                                   str-space->keyword-underline]]))

(defn formatted-data-real-time
  [data]
  (let [price (-> data first second vals first bigdec)
        today-date (aux.t/current-date->keyword)
        historic {today-date price}]
    {:price      price
     :date       today-date
     :updated-at (aux.t/get-millis)
     :historic   historic}))

(defn formatted-data-historic
  [{:keys [prices]}]
  (let [historic (reduce #(let [price (-> %2 second bigdec)
                                date (-> %2 first aux.t/milliseconds->date-keyword)]
                            (assoc %1 date price)) {} prices)
        price (->> historic (sort-by key) last val)
        today-date (->> historic (sort-by key) last key)]
    {:price      price
     :date       today-date
     :updated-at (aux.t/get-millis)
     :historic   historic}))

(defn real-time->internal
  [body]
  (-> body
      (json/parse-string true)
      str-space->keyword-underline
      remove-keyword-parenthesis
      formatted-data-real-time))

(defn historic->internal
  [body]
  (-> body
      (json/parse-string true)
      str-space->keyword-underline
      remove-keyword-parenthesis
      formatted-data-historic

      )
  )
