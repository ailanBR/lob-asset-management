(ns lob-asset-management.controller.market
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.io.http_in :as io.http]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.aux.util :refer [str-space->keyword-underline
                                                   remove-keyword-parenthesis]]
            [lob-asset-management.logic.asset :as l.a]))

(defn format-historic-price
  [price-historic]
  (reduce #(let [val->keyword (-> %2 val str-space->keyword-underline remove-keyword-parenthesis)]
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
                                       str-space->keyword-underline
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
  (if-let [market-info (io.http/get-daily-adjusted-prices asset)]
    market-info
    (throw (ex-info :message "[get-stock-market-price] Something was wrong in get market data"))))

(defn get-crypto-market-price
  [crypto-ticket]
  (if-let [market-info (io.http/get-crypto-price crypto-ticket)]
    market-info
    (throw (ex-info :message "[get-crypto-market-price] Something was wrong in get market data"))))

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
  [{:asset/keys [id]
    current-price :asset.market-price/price
    current-date :asset.market-price/price-date :as asset}
   asset-id
   {:keys [price updated-at date historic]}]
  (if (and (= id asset-id))
    (let [new-price? (and (not (= current-price price)) (not (= current-date date)))
          updated-historic (merge (:asset.market-price/historic asset) historic)]
      (if new-price?
        (-> asset
            (assoc :asset.market-price/price price
                   :asset.market-price/updated-at updated-at
                   :asset.market-price/price-date date
                   :asset.market-price/historic updated-historic)
            (dissoc :asset.market-price/retry-attempts))
        (-> asset
            (assoc :asset.market-price/updated-at updated-at)
            (dissoc :asset.market-price/retry-attempts))))
    asset))

(defn update-assets
  [assets
   {less-updated-asset-id :asset/id}
   market-last-price]
  (let [updated-assets (map #(update-asset % less-updated-asset-id market-last-price)
                            assets)]
    updated-assets))

(defn update-asset-retry-attempt
  [{:asset/keys [id] :as asset
    :asset.market-price/keys [retry-attempts]}
   asset-id]
  (if (= id asset-id)
    (assoc asset :asset.market-price/retry-attempts (+ 1 (or retry-attempts 0)))
    asset))

(defn update-assets-retry-attempt
  [assets
   {less-updated-asset-id :asset/id}]
  (let [updated-assets (map #(update-asset-retry-attempt % less-updated-asset-id)
                            assets)]
    updated-assets))

(defn less-updated-asset
  [assets day-of-week]
  (-> assets (a.a/get-less-market-price-updated {:day-of-week day-of-week}) first))

(defn reset-retry-attempts
  ([]
   (if-let [assets (io.f-in/get-file-by-entity :asset)]
     (reset-retry-attempts assets)
     (log/error "[MARKET-UPDATING] update-asset-market-price - can't get assets")))
  ([assets]
   (let [any-to-reset? (->> assets (filter :asset.market-price/retry-attempts) empty? not)]
     (when any-to-reset?
       (let [update-fn (fn [{:asset.market-price/keys [retry-attempts updated-at] :as asset}]
                         (if (and retry-attempts
                                  updated-at
                                  (l.a/less-updated-than-target? 6 updated-at))
                           (dissoc asset :asset.market-price/retry-attempts)
                           asset))]
         (->> assets
              (map update-fn)
              io.f-out/upsert))))))

(defn update-asset-market-price
  ([]
   (if-let [assets (io.f-in/get-file-by-entity :asset)]
     (update-asset-market-price assets 1)
     (log/error "[MARKET-UPDATING] update-asset-market-price - can't get assets")))
  ([assets day-of-week]
   (if-let [{:asset/keys [ticket] :as less-updated-asset
             :asset.market-price/keys [retry-attempts]} (less-updated-asset assets day-of-week)]
     (try
       (do (log/info "[MARKET-UPDATING] Stating get asset price for " (:asset/ticket less-updated-asset))
           (let [market-last-price (get-market-price less-updated-asset)
                 updated-assets (update-assets assets less-updated-asset market-last-price)]
             (log/info "[MARKET-UPDATING] Success [" ticket "] " (:price market-last-price) " - " (:date market-last-price))
             (io.f-out/upsert updated-assets)
             updated-assets))
       (catch Exception e
         (do (log/error (str (:asset/ticket less-updated-asset) " error in update-asset-market-price " e))
             (if (< (or retry-attempts 0) 3)
               (do
                 (log/info (str "Already retry [" (or retry-attempts 0) "], new attempt after 5sec"))
                 (let [updated-assets (update-assets-retry-attempt assets less-updated-asset)]
                   (io.f-out/upsert updated-assets)
                   (Thread/sleep 5000)
                   (update-asset-market-price updated-assets day-of-week)))
               (log/info (str "Retry limit archived"))))))
     (log/warn "[MARKET-UPDATING] No asset to be updated"))))

#_(defn get-asset-market-price
  "Receive a list of assets and return the list updated without read or write data"
  [assets]
  (if-let [less-updated-asset (-> assets a.a/get-less-market-price-updated first)]
    (do (log/info "[MARKET-PRICE] Stating get asset price for " (:asset/ticket less-updated-asset))
        (let [less-updated-asset-ticket (in-ticket->out-ticket less-updated-asset)
              market-last-price (get-stock-market-price less-updated-asset-ticket)
              updated-assets (update-assets assets less-updated-asset market-last-price)]
          (log/info "[MARKET-UPDATING] Success " less-updated-asset-ticket " price " (:price market-last-price))
          updated-assets))
    (log/warn "[MARKET-UPDATING] No asset to be updated")))

(comment
  (def market-formated (get-stock-market-price "ABEV3.SA"))
  (get-stock-market-price "ABEV3.SA")
  (get-crypto-market-price "BTC")

  (def company-overview (io.http/get-company-overview "ABEV3.SA"))

  (clojure.pprint/pprint market-formated)
  (last-price market-formated)

  (update-asset-market-price)

  (def as (io.f-in/get-file-by-entity :asset))

  (def ap (get-asset-market-price as))
  )