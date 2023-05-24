(ns lob-asset-management.io.http_in
  (:require [clj-http.client :as http]
            [lob-asset-management.relevant :refer [alpha-key]]
            [cheshire.core :as json]
            [schema.core :as s]))

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

(defn get-daily-adjusted-prices
  ([symbol]
   (get-daily-adjusted-prices symbol alpha-key))
  ([symbol api-key]
   (let [{:keys [status body]} (http/get "https://www.alphavantage.co/query"
                            {:query-params {:function "TIME_SERIES_DAILY_ADJUSTED"
                                            :symbol   symbol
                                            :apikey   api-key}})]
     (if (= status 200)
       (-> body
           (json/parse-string true)
           (keyword-space->underline)
           (remove-keyword-parenthesis))
       (throw (ex-info "Failed to get stock price information"
                       {:status (:status status)}))))))

(defn get-company-overview
  ([symbol]
   (get-company-overview symbol alpha-key))
  ([symbol api-key]
   (let [{:keys [status body]} (http/get "https://www.alphavantage.co/query"
                                         {:query-params {:function "OVERVIEW"
                                                         :symbol   symbol
                                                         :apikey   api-key}})]
     (if (= status 200)
       (-> body)
       (throw (ex-info "Failed to get company overview"
                       {:status (:status status)}))))))

(s/defn get-forex-brl->usd
  ([]
   (get-forex-brl->usd :compact alpha-key))
  ([output-size :- s/Keyword]
   (get-forex-brl->usd output-size alpha-key))
  ([output-size  :- s/Keyword
    api-key  :- s/Str]
   (let [{:keys [status body]} (http/get "https://www.alphavantage.co/query"
                                         {:query-params {:function "FX_DAILY"
                                                         :from_symbol "USD"
                                                         :to_symbol   "BRL"
                                                         :outputsize (name output-size)
                                                         :apikey api-key}})]
     (if (= status 200)
       (-> body
           (json/parse-string true)
           (keyword-space->underline)
           (remove-keyword-parenthesis))
       (throw (ex-info "Failed to get usd price"
                       {:status (:status status)}))))))

(comment
  (get-daily-adjusted-prices "CAN")
  (def abev-result (get-daily-adjusted-prices "CAN"))
  (clojure.pprint/pprint abev-result)

  (get abev-result ":Meta Data")

  (def usd (get-forex-brl->usd))

  )