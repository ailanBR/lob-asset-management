(ns lob-asset-management.controller.market
  (:require [lob-asset-management.io.http_in :as io.http]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.aux.time :as aux.t]
            ;[clj-time.core :as t]
            [java-time.api :as t]
            ))

(defn keyword-space->underline [m]
  (zipmap (map #(keyword (clojure.string/replace (name %) " " "_")) (keys m))
          (vals m)))
(defn formatted-data
  [response]
  (let [meta-data (keyword-space->underline (:Meta_Data response))
        time-series (keyword-space->underline (:Time_Series_Daily response))]
    {:meta-data   meta-data
     :time-series time-series}))

(defn format-historic-price
  [price-historic]
  (reduce #(assoc %1 (key %2) (-> %2 val keyword-space->underline :4._close bigdec))
          {}
          price-historic))

(defn last-price
  [formatted-data]
  (if (or (not (nil? formatted-data))
          (not (empty? formatted-data)))
    (if-let [latest-refreshed-dt (-> formatted-data :meta-data :3._Last_Refreshed keyword)]
      (let [latest-refreshed-price (-> formatted-data
                                       :time-series
                                       latest-refreshed-dt
                                       keyword-space->underline
                                       :4._close
                                       bigdec)
            price-historic (-> formatted-data
                               :time-series
                               format-historic-price)]
      {:price      latest-refreshed-price
       :date       latest-refreshed-dt
       :updated-at (aux.t/get-current-millis)
       :historic   price-historic})
      (throw (ex-info "[ERROR] (2) Something was wrong in get market data => formatted-data" formatted-data)))
    (throw (ex-info "[ERROR] (2) Something was wrong in get market data => formatted-data" formatted-data))))

(defn get-b3-market-price
  [asset]
  (if-let [market-info (io.http/get-daily-adjusted-prices asset)]
    (let [formatted-data (formatted-data market-info)
          last-price (last-price formatted-data)]
      last-price)
    (println "[ERROR] Something was wrong in get market data")))

(defn in-ticket->out-ticket
  [{:asset/keys [ticket type]}]
  (let [asset-name (name ticket)]
    (if (or (= type :stockBR) (= type :fii))
      (str asset-name ".SA")
      asset-name)))

(defn update-asset
  [{:asset/keys [id] :as asset}
   asset-id
   {:keys [price updated-at date historic]}]
  (if (= id asset-id)
    (let [updated-historic (first (conj (:asset.market-price/historic asset)
                                        historic))]
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

(defn update-asset-market-price
  ([]
   (if-let [assets (io.f-in/get-file-by-entity :asset)]
     (update-asset-market-price assets)
     (println "[ERROR] update-asset-market-price - can't get assets")))
  ([assets]
   (if-let [less-updated-asset (-> assets (a.a/get-less-market-price-updated 1 1) first)]
     (do (println "[MARKET-UPDATING] Stating get asset price for " less-updated-asset)
         (let [less-updated-asset-ticket (in-ticket->out-ticket less-updated-asset)
               market-last-price (get-b3-market-price less-updated-asset-ticket)
               updated-assets (update-assets assets less-updated-asset market-last-price)]
           (println "[MARKET-UPDATING] Success " less-updated-asset-ticket " price " (:price market-last-price))
           (io.f-out/upsert updated-assets)))
     (println "[WARNING] No asset to be updated"))))

(defn get-asset-market-price
  "Receive a list of assets and return the list updated without read or write data"
  [assets]
  (if-let [less-updated-asset (-> assets (a.a/get-less-market-price-updated) first)]
    (do (println "[MARKET-PRICE] Stating get asset price for " less-updated-asset)
        (let [less-updated-asset-ticket (in-ticket->out-ticket less-updated-asset)
              market-last-price (get-b3-market-price less-updated-asset-ticket)
              updated-assets (update-assets assets less-updated-asset market-last-price)]
          (println "[MARKET-UPDATING] Success " less-updated-asset-ticket " price " (:price market-last-price))
          updated-assets))
    (println "[WARNING] No asset to be updated")))

(comment
  (def aux-market-info (io.http/get-daily-adjusted-prices "CAN"))
  (def market-formated (get-b3-market-price "ABEV3.SA"))

  (def company-overview (io.http/get-company-overview "ABEV3.SA"))

  (clojure.pprint/pprint market-formated)
  (last-price market-formated)

  (update-asset-market-price)

  (def as (io.f-in/get-file-by-entity :asset))

  (def ap (get-asset-market-price as))
  )