(ns lob-asset-management.io.http_in
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [lob-asset-management.adapter.asset :as a.a]
            [net.cgrand.enlive-html :as html]
            [lob-asset-management.relevant :refer [alpha-key]]
            [lob-asset-management.adapter.alpha-vantage-api :as a.ava]
            [lob-asset-management.adapter.web-data-extraction :as a.wde]
            [lob-asset-management.controller.metric :as c.metric]
            [schema.core :as s]))

(defn- remove-credentials
  [query-params]
  (dissoc query-params :apikey))

(defn- http-get
  [endpoint query-params]
  (let [result (http/get endpoint query-params)
        query-params (remove-credentials query-params)]
    (c.metric/add-api-call {:url endpoint :params query-params})
    result))

(defn- html-resource
  [endpoint]
  (let [result (-> endpoint
                   java.net.URL.
                   html/html-resource)]
    (c.metric/add-api-call {:url endpoint})
    result))

(defn get-daily-adjusted-prices
  ([stock]
   (get-daily-adjusted-prices stock alpha-key))
  ([stock api-key]
   (let [symbol (a.a/in-ticket->out-ticket stock)
         {:keys [status body]} (http-get "https://www.alphavantage.co/query"
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

(defn advfn-data-extraction
  "e.g NASDAQ/AAPL or bovespa/ABEV3"
  [ticket]
  (if-let [response (html-resource (a.wde/advfn-url ticket))]
    (a.wde/response->internal response)
    (throw (ex-info "Failed to get stock price using ADVFN information"
                    {:status 999}))))

(defn advfn-data-extraction-br
  [asset]
  (if-let [response (-> asset
                        a.wde/in-ticket->out-ticket
                        a.wde/br-advfn-url
                        html-resource)]
    (a.wde/br-response->internal response)
    (throw (ex-info "Failed to get stock price using ADVFN information"
                    {:status 999}))))

(comment
  (get-daily-adjusted-prices "CAN")
  (def abev-result (get-daily-adjusted-prices "CAN"))
  (clojure.pprint/pprint abev-result)

  (get abev-result ":Meta Data")

  (def usd (get-forex-brl->usd))

  (get-crypto-price-real-time :blockstack)
  ------------------------
  ;Web Scraping =>  https://practical.li/blog/posts/web-scraping-with-clojure-hacking-hacker-news/
  ;----------- Other option https://www.marketscreener.com/quote/stock/AMBEV-S-A-15458762/

  (def resp (advfn-data-extraction-br {:asset/ticket :HGBS11
                                       :asset/type   :stockBR}))

  (a.wde/asset-news resp)
  (def t (-> "https://br.advfn.com/bolsa-de-valores/nasdaq/AAPL/cotacao"
             java.net.URL.
             html/html-resource))

  (->> [:table#id_news]
       (html/select t)
       first
       :content
       (remove #(clojure.string/includes? % "\n"))
       (map #(let [cnt (:content %)
                   dt (-> cnt first :content first)
                   hr (-> cnt second :content first)
                   from (-> cnt rest rest first :content first :content first)
                   txt (-> cnt last :content first :content first)
                   href (clojure.string/join ["https//" (-> cnt last :content first :attrs :href)])]
              {:id   (-> "-"
                         (clojure.string/join [dt hr from])
                         (clojure.string/replace #" " "-")
                         (clojure.string/replace #"/" "-")
                         (clojure.string/replace #":" "-"))
               :txt  txt
               :href href}))
       rest
       ;first
       )
  ;---------------------------- Historic using ADVFN
  ;https://br.advfn.com/bolsa-de-valores/amex/CNBS/historico
  )
