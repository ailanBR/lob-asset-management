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

(defn last-price
  [formatted-data]
  (if (not (empty? formatted-data))
    (let [latest-refreshed-dt (-> formatted-data :meta-data :3._Last_Refreshed keyword)
          latest-refreshed-price (-> formatted-data
                                     :time-series
                                     latest-refreshed-dt
                                     keyword-space->underline
                                     :4._close
                                     bigdec)]
      {:price      latest-refreshed-price
       :date       latest-refreshed-dt
       :updated-at (aux.t/get-current-millis)})
    (do
      (println "[ERROR] Something was wrong in get market data => formatted-data")
      (clojure.pprint/pprint formatted-data))))

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
   (if-let [less-updated-asset (-> assets (a.a/get-less-market-price-updated) first)]
     (do (println "[MARKET-UPDATING] Stating get asset price for " less-updated-asset)
         (let [less-updated-asset-ticket (in-ticket->out-ticket less-updated-asset)
               market-last-price (get-b3-market-price less-updated-asset-ticket)
               updated-assets (update-assets assets less-updated-asset market-last-price)]
           (println "[MARKET-UPDATING] Success " less-updated-asset-ticket " price " (:price market-last-price))
           (io.f-out/upsert updated-assets)))
     (println "[WARNING] No asset to be updated"))))

(defn get-asset-market-price
  "Receive a list of assets and return the list updated without read or write data"
  [asset]
  (let [{:keys [price updated-at date]}
        {:price      11.11M
         :date       :2023-05-05
         :updated-at (aux.t/get-current-millis)}]
    (assoc asset :asset.market-price/price price
                 :asset.market-price/updated-at updated-at
                 :asset.market-price/price-date date)))

(comment
  (def aux-market-info (io.http/get-daily-adjusted-prices "CAN"))
  (def market-formated (get-b3-market-price "ABEV3.SA"))

  (def company-overview (io.http/get-company-overview "ABEV3.SA"))

  (clojure.pprint/pprint market-formated)
  (last-price market-formated)

  (update-asset-market-price)
  ;-----------------------
  (-> #:asset{:id #uuid "710a8d28-8a27-40b0-a067-ba437a8fc4a1",
              :name "BBAS3 - BANCO DO BRASIL S/A",
              :ticket :BBAS3,
              :category [:finance],
              :type :stockBR}
      in-ticket->out-ticket
      get-b3-market-price)
  )