(ns lob-asset-management.controller.market
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.io.http_in :as io.http]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.aux.time :as aux.t]))

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

(defn formatted-data
  [{:keys [Meta_Data Time_Series_Daily Time_Series_Digital_Currency_Daily] :as result}]
  (let [meta-data (keyword-space->underline Meta_Data)
        time-series (keyword-space->underline (or Time_Series_Daily Time_Series_Digital_Currency_Daily))]
    {:meta-data   meta-data
     :time-series time-series}))

(defn format-historic-price
  [price-historic]
  (reduce #(let [val->keyword (-> %2 val keyword-space->underline remove-keyword-parenthesis)]
             (assoc %1 (key %2) (bigdec (or (:4._close val->keyword)
                                             (:4a._close_BRL val->keyword)))))
          {}
          price-historic))

(defn market-info->last-refreshed-dt
  [formatted-data]
  (when-let [latest-refreshed-dt (or (-> formatted-data :meta-data :3._Last_Refreshed)
                                     (-> formatted-data :meta-data :6._Last_Refreshed))]
    (-> latest-refreshed-dt
        (clojure.string/split #" ")
        first
        keyword)))

(defn last-price
  [{:keys [time-series] :as formatted-data}]
  (if (or (not (nil? formatted-data))
          (not (empty? formatted-data)))
    (if-let [latest-refreshed-dt (market-info->last-refreshed-dt formatted-data)]
      (let [latest-refreshed-price (-> time-series
                                       latest-refreshed-dt
                                       keyword-space->underline
                                       remove-keyword-parenthesis)
            closed-price (bigdec (or (:4._close latest-refreshed-price)
                                     (:4a._close_BRL latest-refreshed-price)))
            price-historic (format-historic-price time-series)]
        {:price      closed-price
         :date       latest-refreshed-dt
         :updated-at (aux.t/get-current-millis)
         :historic   price-historic})
      (throw (ex-info "[last-price] (1) Something was wrong in get market data => formatted-data" formatted-data)))
    (throw (ex-info "[last-price] (2) Something was wrong in get market data => formatted-data" formatted-data))))

(defn get-stock-market-price
  [asset]
  (log/info "[get-stock-market-price] started")
  (if-let [market-info (io.http/get-daily-adjusted-prices asset)]
    (let [formatted-data (formatted-data market-info)
          last-price (last-price formatted-data)]
      last-price)
    (log/error "[get-stock-market-price] Something was wrong in get market data")))

(defn get-crypto-market-price
  [crypto-ticket]
  (log/info "[get-crypto-market-price] started")
  (if-let [market-info (io.http/get-crypto-price crypto-ticket)]
    (let [formatted-data (formatted-data market-info)
          last-price (last-price formatted-data)]
      last-price)
    (log/error "[get-crypto-market-price] Something was wrong in get market data")))

(defn in-ticket->out-ticket
  [{:asset/keys [ticket type]}]
  (let [asset-name (name ticket)]
    (if (or (= type :stockBR) (= type :fii))
      (str asset-name ".SA")
      asset-name)))

(defn get-market-price
  [{:asset/keys [type] :as asset}]
  (let [less-updated-asset-ticket (in-ticket->out-ticket asset)]
    (if (= type :crypto)
      (get-crypto-market-price less-updated-asset-ticket)
      (get-stock-market-price less-updated-asset-ticket))))

(defn update-asset
  [{:asset/keys [id] :as asset}
   asset-id
   {:keys [price updated-at date historic]}]
  (if (= id asset-id)
    (let [updated-historic (merge (:asset.market-price/historic asset) historic)]
      (assoc asset :asset.market-price/price price
                   :asset.market-price/updated-at updated-at
                   :asset.market-price/price-date date
                   :asset.market-price/historic updated-historic))
    asset))

(defn update-assets
  [assets
   {less-updated-asset-id :asset/id}
   market-last-price]
  (let [updated-assets (map #(update-asset % less-updated-asset-id market-last-price)
                            assets)]
    updated-assets))

(defn less-updated-asset
  ([]
   (when-let [assets (io.f-in/get-file-by-entity :asset)]
     (less-updated-asset assets)))
  ([assets]
   (-> assets (a.a/get-less-market-price-updated 1 1) first)))

(defn update-asset-market-price
  ([]
   (if-let [assets (io.f-in/get-file-by-entity :asset)]
     (update-asset-market-price assets)
     (log/error "[MARKET-UPDATING] update-asset-market-price - can't get assets")))
  ([assets]
   (if-let [{:asset/keys [ticket] :as less-updated-asset} (-> assets (a.a/get-less-market-price-updated 1 1) first)]
     (do (log/info "[MARKET-UPDATING] Stating get asset price for " (:asset/ticket less-updated-asset))
         (let [market-last-price (get-market-price less-updated-asset)
               updated-assets (update-assets assets less-updated-asset market-last-price)]
           (log/info "[MARKET-UPDATING] Success " ticket " price " (:price market-last-price))
           (io.f-out/upsert updated-assets)
           updated-assets))
     (log/warn "[MARKET-UPDATING] No asset to be updated"))))

#_(defn get-asset-market-price
  "Receive a list of assets and return the list updated without read or write data"
  [assets]
  (if-let [less-updated-asset (-> assets (a.a/get-less-market-price-updated) first)]
    (do (log/info "[MARKET-PRICE] Stating get asset price for " (:asset/ticket less-updated-asset))
        (let [less-updated-asset-ticket (in-ticket->out-ticket less-updated-asset)
              market-last-price (get-stock-market-price less-updated-asset-ticket)
              updated-assets (update-assets assets less-updated-asset market-last-price)]
          (log/info "[MARKET-UPDATING] Success " less-updated-asset-ticket " price " (:price market-last-price))
          updated-assets))
    (log/warn "[MARKET-UPDATING] No asset to be updated")))

(comment
  (def aux-market-info (io.http/get-daily-adjusted-prices "CAN"))
  (def market-formated (get-crypto-market-price :ALGO))

  (def company-overview (io.http/get-company-overview "ABEV3.SA"))

  (clojure.pprint/pprint market-formated)
  (last-price market-formated)

  (update-asset-market-price)

  (def as (io.f-in/get-file-by-entity :asset))

  (def ap (get-asset-market-price as))
  )