(ns lob-asset-management.io.http_in
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [net.cgrand.enlive-html :as html]
            [lob-asset-management.relevant :refer [alpha-key]]
            [lob-asset-management.adapter.alpha-vantage-api :as a.ava]
            [lob-asset-management.adapter.web-data-extraction :as a.wde]
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


(defn web-data-extraction
  "e.g abev3:bs"
  [ticket]
  (try
    (if-let [response (-> "https://tradingeconomics.com/"
                          (str ticket)
                          java.net.URL.
                          html/html-resource)]
      (a.wde/response->internal response)
      (log/error "web-data-extraction error"))
    (catch Exception e
      (log/error "web-data-extraction error " e))))

(comment
  (get-daily-adjusted-prices "CAN")
  (def abev-result (get-daily-adjusted-prices "CAN"))
  (clojure.pprint/pprint abev-result)

  (get abev-result ":Meta Data")

  (def usd (get-forex-brl->usd))

  (get-crypto-price-real-time :blockstack)
  ------------------------
  ;Web Scraping =>  https://practical.li/blog/posts/web-scraping-with-clojure-hacking-hacker-news/
  (def st-url "https://www.advfn.com/stock-market/bovespa/ABEV3/stock-price")
  (def st-r (html/html-resource (java.net.URL. st-url)))
  (-> st-r
      (html/select [:div.price-info])
      first
      :content
      second
      :content)

  ;--------- More info
  (def te-url "https://tradingeconomics.com/abev3:bs")
  (def te-r (html/html-resource (java.net.URL. te-url)))
  (def price (->> [:div.market-header-values]
                  (html/select te-r)
                  first
                  :content
                  (remove #(clojure.string/includes? % "\n"))
                  first
                  :content
                  (remove #(clojure.string/includes? % "\n"))
                  second
                  :content
                  first
                  lob-asset-management.aux.money/safe-number->bigdec))

  (def txt-panel (-> te-r
                     (html/select [:div.panel-default])
                     (nth 5)
                     :content))

  (def price-desc (->> txt-panel
                       (remove #(clojure.string/includes? % "\n"))
                       second
                       :content
                       (remove #(clojure.string/includes? % "\n"))
                       first
                       :content
                       first))

  (def company-desc (->> txt-panel
                         (remove #(clojure.string/includes? % "\n"))
                         last
                         :content
                         (remove #(clojure.string/includes? % "\n"))
                         first
                         ))

  ;----------- Other option https://www.marketscreener.com/quote/stock/AMBEV-S-A-15458762/
  )
