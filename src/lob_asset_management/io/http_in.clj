(ns lob-asset-management.io.http_in
  (:require [clj-http.client :as http]
            [lob-asset-management.relevant :refer [alpha-key]]
            [lob-asset-management.adapter.alpha-vantage-api :as a.ava]
            [lob-asset-management.controller.metric :as c.metric]
            [schema.core :as s]))

(defn- http-get
  [endpoint query-params]
  (let [result (http/get endpoint query-params)]
    (c.metric/add-api-call {:url endpoint :params query-params})
    result))

(defn get-daily-adjusted-prices
  ([symbol]
   (get-daily-adjusted-prices symbol alpha-key))
  ([symbol api-key]
   (let [{:keys [status body]} (http-get "https://www.alphavantage.co/query"
                                    {:query-params {:function "TIME_SERIES_DAILY"
                                                    :symbol   symbol
                                                    :apikey   api-key}})]
     (if (= status 200)
       (a.ava/response->internal body)
       (throw (ex-info "Failed to get stock price information"
                       {:status (:status status)}))))))

(defn get-company-overview
  ([symbol]
   (get-company-overview symbol alpha-key))
  ([symbol api-key]
   (let [{:keys [status body]} (http-get "https://www.alphavantage.co/query"
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
   (let [{:keys [status body]} (http-get "https://www.alphavantage.co/query"
                                         {:query-params {:function "FX_DAILY"
                                                         :from_symbol "USD"
                                                         :to_symbol   "BRL"
                                                         :outputsize (name output-size)
                                                         :apikey api-key}})]
     (if (= status 200)
       (a.ava/response->internal body)
       (throw (ex-info "Failed to get usd price"
                       {:status (:status status)}))))))

(s/defn get-crypto-price
  ([crypto-ticket :- s/Keyword]
   (get-crypto-price crypto-ticket alpha-key))
  ([crypto-ticket  :- s/Keyword
    api-key  :- s/Str]
   (let [{:keys [status body]} (http-get "https://www.alphavantage.co/query"
                                         {:query-params {:function "DIGITAL_CURRENCY_DAILY"
                                                         :symbol   (name crypto-ticket)
                                                         :market   "BRL"
                                                         :apikey   api-key}})]
     (if (= status 200)
       (a.ava/response->internal body)
       (throw (ex-info "Failed to get crypto price"
                       {:status (:status status)}))))))

(s/defn get-crypto-price-real-time
  "Coingecko API

  Limit of 10-30 calls/minute"
  [crypto-ticket]
  (let [crypto-id (name crypto-ticket)
        {:keys [status body]} (http-get "https://api.coingecko.com/api/v3/simple/price"
                                        {:query-params {:ids crypto-id :vs_currencies "BRL"}})]
    (if (= status 200)
      (a.ava/response->internal body)
      (throw (ex-info "Failed to get real time crypto price"
                      {:status (:status status)})))))

(comment
  (get-daily-adjusted-prices "CAN")
  (def abev-result (get-daily-adjusted-prices "CAN"))
  (clojure.pprint/pprint abev-result)

  (get abev-result ":Meta Data")

  (def usd (get-forex-brl->usd))

  (get-crypto-price-real-time :blockstack)

  )