(ns lob-asset-management.adapter.alpha-vantage-api)

(get abev-result ":Meta Data")

(defn despace [m]
  (zipmap (map #(keyword (clojure.string/replace (name %) " " "_")) (keys m))
          (vals m)))

(defn remove-parenteses-b [m]
  (zipmap (map #(keyword (clojure.string/replace (name %) ")" "")) (keys m))
          (vals m)))

(defn remove-parenteses-a [m]
  (zipmap (map #(keyword (clojure.string/replace (name %) "(" "")) (keys m))
          (vals m)))

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
     :date latest-refreshed-dt}))