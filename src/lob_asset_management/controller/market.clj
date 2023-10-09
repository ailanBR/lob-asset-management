(ns lob-asset-management.controller.market
  (:require [clojure.tools.logging :as log]
            [java-time.api :as t]
            [lob-asset-management.db.asset-news :as db.an]
            [lob-asset-management.io.http_in :as io.http]
            [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.db.asset :as db.a]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.controller.telegram-bot :refer [bot] :as t.b]))

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
  (let [get-historic? (or true (-> args first :with-historic) historic)]
    (if-let [{:keys [news] :as market-info}
             (if get-historic?
               (io.http/advfn-data-extraction-br asset)
               ;(-> asset a.wde/in-ticket->out-ticket io.http/advfn-data-extraction)
               (io.http/get-daily-adjusted-prices asset))]
      (do
        (when get-historic?
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

(defn update-asset
  [{:asset/keys [id]
    current-price :asset.market-price/price
    current-date :asset.market-price/price-date
    current-historic :asset.market-price/historic :as asset}
   asset-id
   {:keys [price updated-at date historic] :as t}]
  (if (and (= id asset-id))
    (let [new-price? (and (not (= current-price price))
                          (> price 0M)
                          (<= (aux.t/date-keyword->miliseconds current-date)
                              (aux.t/date-keyword->miliseconds date)))
          updated-historic (update-historic current-historic historic)]
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
  (get-stock-market-price {:asset/ticket :HAPV3,
                           :asset/tax-number "63.554.067/0001-98",
                           :asset/id #uuid "9ab9dba9-b2ff-4857-b50d-9b9aa6219079",
                           :asset.market-price/price 4.29M,
                           :asset.market-price/price-date :2023-09-26,
                           :asset/category [:health],
                           :asset.market-price/updated-at 1695748832020,
                           :asset.market-price/historic
                           {:2023-05-02 2.7900M,
                            :2023-08-18 4.6300M,
                            :2023-03-23 2.0700M,
                            :2023-07-04 4.3200M,
                            :2023-03-14 2.4800M,
                            :2023-03-02 2.92M,
                            :2023-01-24 4.48M,
                            :2023-06-29 4.2000M,
                            :2023-04-10 2.3500M,
                            :2023-08-21 4.4900M,
                            :2023-02-02 5.11M,
                            :2023-04-18 2.4000M,
                            :2023-09-14 4.49M,
                            :2023-09-25 4.38M,
                            :2023-06-02 4.1200M,
                            :2023-08-10 5.2600M,
                            :2023-04-06 2.3700M,
                            :2023-02-09 4.49M,
                            :2023-05-17 3.4800M,
                            :2023-08-29 4.6000M,
                            :2023-03-27 2.2200M,
                            :2023-03-24 2.2500M,
                            :2023-02-03 4.63M,
                            :2023-06-13 3.9700M,
                            :2023-09-12 4.62M,
                            :2023-06-20 4.2000M,
                            :2023-07-28 4.6200M,
                            :2023-09-08 4.3700M,
                            :2023-04-03 2.4500M,
                            :2023-06-09 4.2400M,
                            :2023-08-11 5.2300M,
                            :2023-08-28 4.5400M,
                            :2023-04-29 2.7600M,
                            :2023-08-17 4.5700M,
                            :2023-01-03 4.51M,
                            :2023-05-23 3.6500M,
                            :2023-07-17 4.1800M,
                            :2023-05-03 2.7700M,
                            :2023-06-28 4.1400M,
                            :2023-02-28 4.49M,
                            :2023-09-20 4.43M,
                            :2023-09-18 4.44M,
                            :2023-05-04 2.8400M,
                            :2023-02-24 4.75M,
                            :2023-06-19 4.2800M,
                            :2023-07-10 4.3200M,
                            :2023-07-21 4.2600M,
                            :2023-07-13 4.3000M,
                            :2023-02-14 4.58M,
                            :2023-07-11 4.4500M,
                            :2023-03-10 2.4700M,
                            :2023-07-03 4.4200M,
                            :2023-03-08 2.9200M,
                            :2023-08-30 4.4700M,
                            :2023-09-13 4.56M,
                            :2023-08-08 5.0600M,
                            :2023-06-07 4.2400M,
                            :2023-01-05 4.77M,
                            :2023-07-24 4.3400M,
                            :2023-02-17 5.09M,
                            :2023-05-24 3.5500M,
                            :2023-04-20 2.5900M,
                            :2023-09-11 4.43M,
                            :2023-07-25 4.4400M,
                            :2023-01-18 4.21M,
                            :2023-05-05 2.9300M,
                            :2023-07-27 4.5400M,
                            :2023-08-24 4.6900M,
                            :2023-08-03 4.6200M,
                            :2023-05-09 2.8800M,
                            :2023-08-22 4.5600M,
                            :2023-02-06 4.6M,
                            :2023-04-05 2.3600M,
                            :2023-02-23 4.83M,
                            :2023-03-29 2.7600M,
                            :2023-08-09 4.9600M,
                            :2023-09-04 4.4500M,
                            :2023-06-05 4.1600M,
                            :2023-06-12 4.1200M,
                            :2023-04-25 2.5600M,
                            :2023-08-07 4.7700M,
                            :2023-07-14 4.1300M,
                            :2023-06-21 4.1900M,
                            :2023-08-25 4.5900M,
                            :2023-01-25 4.58M,
                            :2023-08-16 4.7300M,
                            :2023-07-06 4.3100M,
                            :2023-01-12 4.35M,
                            :2023-04-19 2.3600M,
                            :2023-03-13 2.5000M,
                            :2023-05-15 3.1500M,
                            :2023-09-06 4.4000M,
                            :2023-01-26 4.44M,
                            :2023-08-02 4.7500M,
                            :2023-03-30 2.8000M,
                            :2023-03-15 2.5200M,
                            :2023-05-18 3.6700M,
                            :2023-08-01 4.8400M,
                            :2023-06-16 4.3500M,
                            :2023-05-22 3.7000M,
                            :2023-07-26 4.6000M,
                            :2023-03-01 3.02M,
                            :2023-05-31 3.9900M,
                            :2023-06-30 4.3800M,
                            :2023-02-07 4.65M,
                            :2023-01-31 5.15M,
                            :2023-07-07 4.3700M,
                            :2023-05-06 2.9300M,
                            :2023-09-05 4.4100M,
                            :2023-09-02 4.4300M,
                            :2023-07-20 4.1600M,
                            :2023-01-17 4.15M,
                            :2023-03-06 2.72M,
                            :2023-02-16 5.13M,
                            :2023-03-31 2.6200M,
                            :2023-03-22 2.2100M,
                            :2023-04-14 2.6000M,
                            :2023-06-15 4.4600M,
                            :2023-01-10 4.31M,
                            :2023-08-15 5.0200M,
                            :2023-03-17 2.3700M,
                            :2022-12-29 5.08M,
                            :2023-02-08 4.48M,
                            :2023-03-21 2.2200M,
                            :2023-06-23 4.3300M,
                            :2023-08-04 4.7600M,
                            :2023-01-02 4.64M,
                            :2023-05-11 3.0700M,
                            :2023-03-07 2.68M,
                            :2023-04-24 2.6100M,
                            :2023-05-10 3.0200M,
                            :2023-01-19 4.37M,
                            :2023-05-25 3.9400M,
                            :2023-05-30 4.0600M,
                            :2023-07-05 4.3800M,
                            :2023-01-11 4.6M,
                            :2023-01-23 4.23M,
                            :2023-06-22 4.2300M,
                            :2023-01-16 4.12M,
                            :2023-02-27 4.55M,
                            :2023-01-20 4.38M,
                            :2023-05-16 3.4100M,
                            :2023-09-01 4.4300M,
                            :2023-08-14 5.0200M,
                            :2023-05-29 3.9000M,
                            :2023-04-28 2.7600M,
                            :2023-07-12 4.3400M,
                            :2023-08-31 4.2600M,
                            :2023-01-13 4.25M,
                            :2023-03-03 2.68M,
                            :2023-05-19 3.6100M,
                            :2023-04-26 2.4900M,
                            :2023-06-26 4.1100M,
                            :2023-03-20 2.1800M,
                            :2023-04-27 2.5600M,
                            :2023-04-17 2.5400M,
                            :2023-04-12 2.6800M,
                            :2023-06-14 4.3000M,
                            :2023-07-19 4.1300M,
                            :2023-06-06 4.3500M,
                            :2023-03-16 2.6400M,
                            :2023-01-06 4.72M,
                            :2023-05-12 3.1200M,
                            :2023-02-15 4.93M,
                            :2023-04-13 2.6400M,
                            :2023-09-26 4.29M,
                            :2023-05-26 3.9000M,
                            :2023-02-13 4.58M,
                            :2023-05-08 2.9200M,
                            :2023-02-10 4.54M,
                            :2023-04-04 2.4800M,
                            :2023-07-18 4.1800M,
                            :2023-01-04 4.58M,
                            :2023-06-01 4.1000M,
                            :2023-03-09 1.9400M,
                            :2023-01-30 4.7M,
                            :2023-06-27 4.0800M,
                            :2023-01-09 4.2M,
                            :2023-02-01 5.05M,
                            :2023-01-27 4.6M,
                            :2023-03-28 2.6300M,
                            :2023-07-31 4.8000M,
                            :2023-08-23 4.6900M,
                            :2023-04-11 2.6500M,
                            :2023-02-22 4.94M},
                           :asset/name "Hapvida",
                           :asset/type :stockBR})

  (def company-overview (io.http/get-company-overview "ABEV3.SA"))

  (clojure.pprint/pprint market-formated)

  (update-asset-market-price)

  (def as (db.a/get-all))

  (def ap (get-asset-market-price as))

  (->> (db.a/get-all)
       (filter #(= :STX (:asset/ticket %)))
       update-asset-market-price)
  )
