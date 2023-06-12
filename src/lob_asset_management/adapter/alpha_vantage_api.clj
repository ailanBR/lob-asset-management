(ns lob-asset-management.adapter.alpha-vantage-api
  (:require [cheshire.core :as json]))

(defn keyword-space->underline [m]
  (zipmap (map #(keyword (clojure.string/replace (name %) " " "_")) (keys m))
          (vals m)))

(defn remove-close-parenthesis [m]
  (zipmap (map #(keyword (clojure.string/replace (name %) ")" "")) (keys m))
          (vals m)))

(defn remove-open-parenthesis [m]
  (zipmap (map #(keyword (clojure.string/replace (name %) "(" "")) (keys m))
          (vals m)))

(defn remove-keyword-parenthesis
  [m]
  (-> m
      remove-close-parenthesis
      remove-open-parenthesis))

(defn response->internal
  [body]
  (-> body
      (json/parse-string true)
      (keyword-space->underline)
      (remove-keyword-parenthesis)))



(comment
  (get abev-result ":Meta Data")
  (def formatted-data
    (let [mains (-> abev-result despace remove-parenteses-b remove-parenteses-a)
          meta-data (despace (:Meta_Data mains))
          time-serie (despace (:Time_Series_Daily mains))]
      {:meta-data   meta-data
       :time-series time-serie}))

  (def last-price
    (let [latest-refreshed-dt (-> formatted-data :meta-data :3._Last_Refreshed keyword)
          latest-refreshed-price (-> formatted-data :time-series latest-refreshed-dt despace :4._close bigdec)]
      {:price latest-refreshed-price
       :date  latest-refreshed-dt}))
  )