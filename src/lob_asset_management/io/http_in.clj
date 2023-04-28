(ns lob-asset-management.io.http_in
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(defn get-daily-adjusted-prices [symbol api-key]
  (let [response (http/get "https://www.alphavantage.co/query"
                           {:query-params {:function "TIME_SERIES_DAILY_ADJUSTED"
                                           :symbol symbol
                                           :apikey api-key}})]
    (if (= (:status response) 200)
      (do
        (clojure.pprint/pprint response)
        (-> response
            :body
            (json/parse-string true)))
      (throw (ex-info "Failed to get stock price information"
                      {:status (:status response)})))))

(comment
  (def abev-result (get-daily-adjusted-prices "ABEV3.SAO" lob-asset-management.relevant/alpha-key))
  (clojure.pprint/pprint abev-result)

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

  )