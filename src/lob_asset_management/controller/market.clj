(ns lob-asset-management.controller.market
  (:require [lob-asset-management.io.http_in :as io.http]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.adapter.asset :as a.a]
            [java-time.api :as t]))

(defn keyword-space->underline [m]
  (zipmap (map #(keyword (clojure.string/replace (name %) " " "_")) (keys m))
          (vals m)))
(defn formatted-data
  [response]
  (let [meta-data (keyword-space->underline (:Meta_Data response))
        time-series (keyword-space->underline (:Time_Series_Daily response))]
    {:meta-data   meta-data
     :time-series time-series}))

(defn last-price
  [formatted-data]
  (if (not (empty? formatted-data))
    (do (println formatted-data)
        (let [latest-refreshed-dt (-> formatted-data :meta-data :3._Last_Refreshed keyword)
              latest-refreshed-price (-> formatted-data
                                         :time-series
                                         latest-refreshed-dt
                                         keyword-space->underline
                                         :4._close
                                         bigdec)]
          {:price      latest-refreshed-price
           :date       latest-refreshed-dt
           :updated-at (str (t/local-date-time))}))
    (do
      (println "[ERROR] Something was wrong in get market data => formatted-data")
      (clojure.pprint/pprint formatted-data))))


(defn get-b3-market-price
  [asset]
  (if-let [market-info (io.http/get-daily-adjusted-prices asset)]
    (let [formatted-data (formatted-data market-info)
          last-price (last-price formatted-data)]
      last-price)
    (do
      (println "[ERROR] Something was wrong in get market data"))))

(defn less-updated-asset->out-ticket
  [less-updated-asset]
  (-> less-updated-asset
      :asset/ticket
      name
      (str ".SA")))

(defn update-assets
  [assets
   {less-updated-asset-id :asset/id}
   {:keys [price updated-at date]}]
  (let [updated-assets (map #(if (= (:asset/id %) less-updated-asset-id)
                               (assoc % :asset.market-price/price price
                                        :asset.market-price/updated-at updated-at
                                        :asset.market-price/price-date date)
                               %)
                            assets)]
    updated-assets))

(defn update-asset-market-price
  ([]
   (if-let [assets (io.f-in/get-file-by-entity :asset)]
     (update-asset-market-price assets)
     (println "[ERROR] update-asset-market-price - can't get assets")))
  ([assets]
   (let [less-updated-asset (a.a/get-less-market-price-updated assets)
         less-updated-asset-ticket (less-updated-asset->out-ticket less-updated-asset)
         market-last-price (get-b3-market-price less-updated-asset-ticket)
         updated-assets (update-assets assets less-updated-asset market-last-price)]
     (clojure.pprint/pprint market-last-price)
     (io.f-out/upsert updated-assets))))

(defn get-asset-market-price
  "Receive a list of assets and return the list updated without read or write data"
  [asset]
  (let [{:keys [price updated-at date]}
        {:price      11.11M
         :date       :2023-05-05
         :updated-at (str (t/local-date-time))}]
    (assoc asset :asset.market-price/price price
                 :asset.market-price/updated-at updated-at
                 :asset.market-price/price-date date)))

(comment


  (def aux-market-info (io.http/get-daily-adjusted-prices "ABEV3.SA"))
  (def market-formated (get-b3-market-price "ABEV3.SA"))
  (clojure.pprint/pprint market-formated)
  (last-price market-formated)
  ;
  ;1. Get assets
  (def assets-file (io.f-in/get-file-by-entity :asset))
  ;2. Get less updated
  (def less-updated-asset (a.a/get-less-market-price-updated assets-file))
  ;3. Get out ticket (Ticket for alpha vantage)
  (def less-updated-asset->out-ticket
    (-> less-updated-asset
        :asset/ticket
        name
        (str ".SA")))
  ;4. Get market price
  (def market-last-price (get-b3-market-price less-updated-asset->out-ticket))
  ;5. Update asset
  (def updated-assets
    (map #(if (= (:asset/id %) (:asset/id less-updated-asset))
            (assoc % :asset.market-price/price (:price market-last-price)
                     :asset.market-price/updated-at (:updated-at market-last-price)
                     :asset.market-price/date (:date market-last-price))
            %)
            assets-file))
  ;6. update the file

  (update-asset-market-price)


  )