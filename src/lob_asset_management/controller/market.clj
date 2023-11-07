(ns lob-asset-management.controller.market
  (:require [clojure.tools.logging :as log]
            [java-time.api :as t]
            [lob-asset-management.db.asset-news :as db.an]
            [lob-asset-management.io.http_in :as io.http]
            [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.db.asset :as db.a]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.aux.util :refer [abs]]
            [lob-asset-management.controller.telegram-bot :refer [bot] :as t.b]))

(defn expected-historic
  []

  )

(defn- asset-news-new-one
  [received-news]
  (let [stored-news (db.an/get-by-ticket (-> received-news first :asset-news/ticket))]
    (if (empty? stored-news)
      received-news
      (let [stored-ids (->> stored-news (map :asset-news/id) set)
            new-ones (remove #(contains? stored-ids (:asset-news/id %)) received-news)]
        new-ones))))

(defn update-news
  [{:asset/keys [ticket name]} news]
  (when (not (empty? news))
    (when-let [asset-news (->> news
                               (a.a/external-news->internal ticket name)
                               asset-news-new-one
                               (remove #(not (:asset-news/id %))))]
      (when (not (empty? asset-news))
        (t.b/asset-news-message asset-news bot)
        (db.an/upsert-bulk! asset-news)))))

(defn get-stock-market-price
  [{:asset.market-price/keys [historic] :as asset} & args]
  (let [get-real-time? (or (-> args first :with-historic) historic)]
    (if-let [{:keys [news] :as market-info} (if get-real-time?
                                              (io.http/advfn-data-extraction-br asset)
                                              ;(io.http/get-daily-adjusted-prices asset)
                                              (io.http/advfn-data-historic-extraction-br asset))]
      (do
        (when get-real-time?
          (update-news asset news))
        market-info)
      (throw (ex-info :message "[get-stock-market-price] Something was wrong in get market data")))))

(defn get-crypto-market-price
  [{:asset.market-price/keys [historic] :as asset}]
  (if-let [market-info (if historic
                         (-> asset a.a/in-ticket->out-crypto-id io.http/get-crypto-price-real-time)
                         (-> asset a.a/in-ticket->out-ticket io.http/get-crypto-price))]
    market-info
    (throw (ex-info :message "[get-crypto-market-price] Something was wrong in get market data"))))

(defn get-market-price
  "options
    :with-historic => Get historic data using alpha api "
  [{:asset/keys [type] :as asset} & args]
  (if (= type :crypto)
    (get-crypto-market-price asset)
    (get-stock-market-price asset args)))

(defn- update-historic
  [historic-list new-historic]
  (->> new-historic
       (merge historic-list)
       (sort-by first)
       (into (sorted-map))))

(defn new-price?
  [{current-price :asset.market-price/price
    current-date :asset.market-price/price-date}
   {:keys [price date]}]
  (and (not (= current-price price))
       (> price 0M)
       (<= (aux.t/date-keyword->miliseconds current-date)
           (aux.t/date-keyword->miliseconds date))))

(defn price-change-percentage
  [current-price
   new-price]
  (-> new-price float (* 100) (/ (float current-price)) (- 100) float))

(defn historic-validation
  [current-price
   historic]
  ())

(defn price-change-validation
  "TODO : Validate the date"
  [{:asset/keys [ticket]
    current-price :asset.market-price/price :as asset}
   {:keys [price] :as new-data}]
  (let [change-percentage (price-change-percentage current-price price)
        change-percentage-abs (abs change-percentage)]
    (if (> change-percentage-abs 100)
      (throw (ex-info (str "Price Change Validation above threshold for " ticket " " change-percentage " %")
                      {:price-changed-percentage change-percentage
                       :threshold                100
                       :ticket                   ticket
                       :current-price            current-price
                       :new-price                price}))
      (when (> change-percentage-abs 10)
        (t.b/asset-price-changed asset new-data change-percentage)))))

(defn update-asset
  [{:asset/keys      [id]
    current-historic :asset.market-price/historic :as asset}
   asset-id
   {:keys [price updated-at date historic] :as new-data}]
  (if (and (= id asset-id))
    (let [new-price? (new-price? asset new-data)
          updated-historic (update-historic current-historic historic)]
      (if new-price?
        (do
          (price-change-validation asset new-data)
          (-> asset
              (assoc :asset.market-price/price price
                     :asset.market-price/updated-at updated-at
                     :asset.market-price/price-date date
                     :asset.market-price/historic updated-historic)
              (dissoc :asset.market-price/retry-attempts)))
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
   (if-let [assets (db.a/get-all)]
     (reset-retry-attempts assets)
     (log/error "[MARKET-UPDATING] update-asset-market-price - can't get assets")))
  ([assets]
   (let [any-to-reset? (->> assets (filter :asset.market-price/retry-attempts) empty? not)]
     (when any-to-reset?
       (let [update-fn (fn [{:asset.market-price/keys [retry-attempts updated-at] :as asset}]
                         (if (and retry-attempts
                                  updated-at
                                  (aux.t/less-updated-than-target? 6 updated-at))
                           (dissoc asset :asset.market-price/retry-attempts)
                           asset))]
         (->> assets
              (map update-fn)
              db.a/upsert!))))))

(defn update-asset-updated-at
  [{:asset/keys [id] :as asset}
   asset-id]
  (if (= id asset-id)
    (assoc asset :asset.market-price/updated-at (aux.t/get-millis
                                                  (t/plus (t/local-date-time) (t/hours 5))))
    asset))

(defn update-assets-updated-at
  [assets
   {less-updated-asset-id :asset/id}]
  (let [updated-assets (map #(update-asset-updated-at % less-updated-asset-id)
                            assets)]
    updated-assets))

(defn- handle-alpha-api-limit-error
  [assets less-updated-asset]
  (let [updated-assets (update-assets-updated-at assets less-updated-asset)]
    (log/error "[MARKET-UPDATING] Alpha API limit have reached")
    (db.a/upsert! updated-assets)))

(defn handle-retry-attempt
  [{:asset.market-price/keys [retry-attempts] :as less-updated-asset}
   assets]
  (log/info (str "Already retry [" (or retry-attempts 0) "], new attempt after 5sec"))
  (let [updated-assets (update-assets-retry-attempt assets less-updated-asset)]
    (db.a/upsert! updated-assets)
    (Thread/sleep 5000)
    updated-assets))

(defn update-asset-market-price
  ([]
   (if-let [assets (db.a/get-all)]
     (update-asset-market-price assets 1)
     (log/error "[MARKET-UPDATING] update-asset-market-price - can't get assets")))
  ([assets]
   (update-asset-market-price assets 1))
  ([assets day-of-week & args]
   (if-let [{:asset/keys [ticket] :as less-updated-asset
             :asset.market-price/keys [retry-attempts]} (less-updated-asset assets day-of-week)]
     (try
       (log/info "[MARKET-UPDATING] Starting get asset price for " (:asset/ticket less-updated-asset))
       (let [market-last-price (get-market-price less-updated-asset)
             updated-assets (update-assets assets less-updated-asset market-last-price)]
         (log/info "[MARKET-UPDATING] Success [" ticket "] " (:price market-last-price) " - " (:date market-last-price))
         (db.a/upsert! updated-assets))
       (catch Exception e
         #_(t.b/send-error-command bot e)
         (if (contains? (-> e ex-data :causes) :alpha-api-limit)
           (handle-alpha-api-limit-error assets less-updated-asset)
           (do (log/error (str (:asset/ticket less-updated-asset) " error in update-asset-market-price " e))
               (if (< (or retry-attempts 0) 3)
                 (-> less-updated-asset
                     (handle-retry-attempt assets)
                     (update-asset-market-price day-of-week args))
                 (log/info (str "Retry limit archived")))))))
     (log/warn "[MARKET-UPDATING] No asset to be updated"))))

(defn update-crypto-market-price
  ([]
   (if-let [assets (db.a/get-all)]
     (update-crypto-market-price assets)
     (log/error "[MARKET-UPDATING] update-asset-market-price - can't get assets")))
  ([assets]
   (->> assets
        (filter #(= :crypto (:asset/type %)))
        update-asset-market-price)))

(comment
  (get-stock-market-price {:asset/ticket :GOOGL,
                           :asset/tax-number nil,
                           :asset/id #uuid "5b3b1b1d-b499-47b9-bf89-176914bfe896",
                           :asset.market-price/price 13708.01M,
                           :asset.market-price/price-date :2023-10-23,
                           :asset/category [:ti],
                           :asset.market-price/updated-at 1698099893468,
                           :asset.market-price/historic
                           {:2023-05-02 105.3200M,
                            :2023-08-18 127.4600M,
                            :2023-03-23 105.6000M,
                            :2023-03-14 93.9700M,
                            :2023-03-02 92.0M,
                            :2023-01-24 97.7M,
                            :2023-06-29 119.1000M,
                            :2023-04-10 106.4400M,
                            :2023-08-21 128.3700M,
                            :2023-02-02 107.74M,
                            :2023-04-18 104.5000M,
                            :2023-09-14 138.1000M,
                            :2023-09-25 131.1100M,
                            :2023-06-02 124.6700M,
                            :2022-12-30 88.23M,
                            :2023-08-10 129.6900M,
                            :2023-10-05 135.0700M,
                            :2023-04-06 108.4200M,
                            :2023-02-09 95.01M,
                            :2023-10-18 137.9600M,
                            :2023-05-17 120.8400M,
                            :2023-08-29 134.5700M,
                            :2023-03-27 102.4600M,
                            :2023-03-24 105.4400M,
                            :2023-02-03 104.78M,
                            :2023-10-03 132.4300M,
                            :2023-06-13 123.8300M,
                            :2023-09-12 135.3400M,
                            :2023-06-20 123.1000M,
                            :2023-10-02 134.1700M,
                            :2023-07-28 132.5800M,
                            :2023-09-19 138.0400M,
                            :2023-09-08 136.3800M,
                            :2023-04-03 104.3600M,
                            :2023-06-09 122.2300M,
                            :2023-08-11 129.5600M,
                            :2023-08-28 131.0100M,
                            :2023-10-04 135.2400M,
                            :2023-10-11 140.5500M,
                            :2023-08-17 129.9200M,
                            :2023-01-03 89.12M,
                            :2023-05-23 122.5600M,
                            :2023-07-17 124.6500M,
                            :2023-05-03 105.4100M,
                            :2023-06-28 120.1800M,
                            :2023-02-28 90.06M,
                            :2023-10-23 13708.01M,
                            :2023-09-20 133.7400M,
                            :2023-09-18 138.2100M,
                            :2023-10-13 137.3600M,
                            :2023-05-04 104.6900M,
                            :2023-02-24 89.13M,
                            :2023-07-10 116.4500M,
                            :2023-07-21 120.0200M,
                            :2023-09-15 137.4000M,
                            :2023-04-21 105.4100M,
                            :2023-07-13 124.5400M,
                            :2023-02-21 91.79M,
                            :2023-02-14 94.68M,
                            :2023-07-11 117.1400M,
                            :2023-03-10 90.6300M,
                            :2023-07-03 119.9000M,
                            :2023-03-08 94.2500M,
                            :2023-08-30 135.8800M,
                            :2023-09-13 136.7100M,
                            :2023-08-08 131.4000M,
                            :2023-06-07 122.5000M,
                            :2023-01-05 86.2M,
                            :2023-07-24 121.5300M,
                            :2023-02-17 94.35M,
                            :2023-05-24 120.9000M,
                            :2023-04-20 105.2900M,
                            :2023-09-11 136.9200M,
                            :2023-07-25 122.2100M,
                            :2023-01-18 91.12M,
                            :2023-05-05 105.5700M,
                            :2023-07-27 129.4000M,
                            :2023-08-24 129.7800M,
                            :2023-08-03 128.4500M,
                            :2023-05-09 107.3500M,
                            :2023-08-22 129.0800M,
                            :2023-02-06 102.9M,
                            :2023-04-05 104.4700M,
                            :2023-02-23 90.89M,
                            :2023-03-29 101.3900M,
                            :2023-08-09 129.6600M,
                            :2023-06-08 122.1400M,
                            :2023-06-05 126.0100M,
                            :2023-06-12 123.6400M,
                            :2023-04-25 103.8500M,
                            :2023-05-01 107.2000M,
                            :2023-08-07 131.5300M,
                            :2023-07-14 125.4200M,
                            :2023-06-21 120.5500M,
                            :2023-08-25 129.8800M,
                            :2023-01-25 95.22M,
                            :2023-08-16 128.7000M,
                            :2023-07-06 120.1100M,
                            :2023-01-12 91.13M,
                            :2023-04-19 104.1800M,
                            :2023-03-13 91.1100M,
                            :2023-05-15 116.5100M,
                            :2023-09-06 134.4600M,
                            :2023-10-16 139.0950M,
                            :2023-10-10 138.0600M,
                            :2023-01-26 97.52M,
                            :2023-10-17 139.7200M,
                            :2023-08-02 128.3800M,
                            :2023-03-30 100.8900M,
                            :2023-03-15 96.1100M,
                            :2023-05-18 122.8300M,
                            :2023-08-01 131.5500M,
                            :2023-06-16 123.5300M,
                            :2023-05-22 125.0500M,
                            :2023-07-26 129.2700M,
                            :2023-03-01 90.36M,
                            :2023-05-31 122.8700M,
                            :2023-06-30 119.7000M,
                            :2023-02-07 107.64M,
                            :2023-01-31 98.84M,
                            :2023-07-07 119.4800M,
                            :2023-10-12 138.9700M,
                            :2023-09-05 135.7700M,
                            :2023-07-20 119.2000M,
                            :2023-01-17 91.29M,
                            :2023-03-06 95.1300M,
                            :2023-02-16 95.51M,
                            :2023-09-28 132.3100M,
                            :2023-10-06 137.5800M,
                            :2023-03-31 103.7300M,
                            :2023-03-22 103.3700M,
                            :20230901 135.38M,
                            :2023-04-14 108.8700M,
                            :2023-06-15 125.0900M,
                            :2023-01-10 88.42M,
                            :2023-08-15 129.7800M,
                            :2023-03-17 101.6200M,
                            :2022-12-29 88.45M,
                            :2023-02-08 99.37M,
                            :2023-03-21 104.9200M,
                            :2023-06-23 122.3400M,
                            :2023-08-04 128.1100M,
                            :2023-05-11 116.5700M,
                            :2023-03-07 93.8600M,
                            :2023-04-24 105.9700M,
                            :2023-09-27 130.5400M,
                            :2023-05-10 111.7500M,
                            :2023-10-19 137.7500M,
                            :2023-01-19 93.05M,
                            :2023-05-25 123.4800M,
                            :2023-09-07 135.2600M,
                            :2023-05-30 123.6700M,
                            :2023-07-05 121.7500M,
                            :2023-01-11 91.52M,
                            :2023-01-23 99.79M,
                            :2023-06-22 123.1500M,
                            :2023-09-29 130.8600M,
                            :2023-02-27 89.87M,
                            :2023-10-09 138.4200M,
                            :2023-01-20 98.02M,
                            :2023-05-16 119.5100M,
                            :2023-09-01 135.6600M,
                            :2023-08-14 131.3300M,
                            :2023-04-28 107.3400M,
                            :2023-07-12 118.9300M,
                            :2023-08-31 136.1700M,
                            :2023-01-13 92.12M,
                            :2023-03-03 93.65M,
                            :2023-05-19 122.7600M,
                            :2023-09-21 130.4400M,
                            :2023-04-26 103.7100M,
                            :2023-06-26 118.3400M,
                            :2023-03-20 101.2200M,
                            :2023-04-27 107.5900M,
                            :2023-04-17 105.9700M,
                            :2023-04-12 104.6400M,
                            :2023-06-14 123.6700M,
                            :2023-10-20 135.6000M,
                            :2023-07-19 122.0300M,
                            :2023-06-06 127.3100M,
                            :2023-03-16 100.3200M,
                            :2023-01-06 87.34M,
                            :2023-05-12 117.5100M,
                            :2023-02-15 96.94M,
                            :2023-04-13 107.4300M,
                            :2023-09-26 128.5650M,
                            :2023-05-26 124.6100M,
                            :2023-02-13 94.61M,
                            :2023-05-08 107.7700M,
                            :2023-02-10 94.57M,
                            :2023-04-04 104.7200M,
                            :2023-07-18 123.7600M,
                            :2023-01-04 88.08M,
                            :2023-06-01 123.7200M,
                            :2023-09-22 130.2500M,
                            :2023-03-09 92.3200M,
                            :2023-01-30 96.94M,
                            :2023-06-27 118.3300M,
                            :2023-01-09 88.02M,
                            :2023-02-01 100.43M,
                            :2023-01-27 99.37M,
                            :2023-03-28 101.0300M,
                            :2023-07-31 132.7200M,
                            :2023-08-23 132.3700M,
                            :2023-04-11 105.3500M,
                            :2023-02-22 91.65M},
                           :asset/name "GOOGL",
                           :asset/type :stockEUA})
  (update-asset-market-price)
  (def company-overview (io.http/get-company-overview "ABEV3.SA"))

  (clojure.pprint/pprint market-formated)

  (update-asset-market-price)

  (def as (db.a/get-all))

  (def ap (get-asset-market-price as))

  (->> (db.a/get-all)
       (filter #(= :STX (:asset/ticket %)))
       update-asset-market-price)
  )
