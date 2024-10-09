(ns lob-asset-management.controller.market
  (:require [clojure.tools.logging :as log]
            [java-time.api :as t]
            [lob-asset-management.db.asset-news :as db.an]
            [lob-asset-management.io.http_in :as io.http]
            [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.db.asset :as db.a]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.aux.util :refer [abs log-colors]]
            [lob-asset-management.controller.telegram-bot :refer [bot] :as t.b]))

(defn- asset-news-new-one
  [received-news]
  (let [received-ids (map :asset-news/id received-news)
        stored-news (db.an/get-by-ids received-ids)]
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
  [asset get-historic?]
  (if-let [{:keys [news] :as market-info} (if get-historic?
                                            (io.http/advfn-data-historic-extraction-br asset)
                                            (io.http/advfn-data-extraction-br asset)
                                            #_(io.http/get-daily-adjusted-prices asset))]
    (if get-historic?
      market-info
      (do (update-news asset news)
          market-info))
    (throw (ex-info :message "[get-stock-market-price] Something was wrong in get market data"))))

(defn get-crypto-market-price
  [asset get-historic?]
  (if-let [{:keys [updated-at] :as market-info} (if get-historic?
                                                  #_(-> asset a.a/in-ticket->out-ticket io.http/get-crypto-price) ;FIXME: {:Error_Message Invalid API call. Please retry or visit the documentation (https://www.alphavantage.co/documentation/) for DIGITAL_CURRENCY_DAILY.}
                                                  (-> asset a.a/in-ticket->out-crypto-id io.http/get-crypto-price-historic)
                                                  (-> asset a.a/in-ticket->out-crypto-id io.http/get-crypto-price-real-time)
                                                  )]
    market-info
    (throw (ex-info :message "[get-crypto-market-price] Something was wrong in get market data"))))

(defn get-historic?
  [{:asset.market-price/keys [historic]}
   args]
  (or (nil? historic)
      (boolean (and args (-> args first :with-historic)))))

(defn get-market-price
  "options
    :with-historic => Get historic data using alpha api "
  [{:asset/keys [type] :as asset} & args]
  (let [get-historic? (get-historic? asset args)]
    (log/info "[get-stock-market-price] get-historic? " get-historic?)
    (if (= type :crypto)
      (get-crypto-market-price asset get-historic?)
      (get-stock-market-price asset get-historic?))))

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
  (if (or (nil? current-date) (nil? current-date))
    true
    (and (not (= current-price price))
         (> price 0M)
         (<= (aux.t/date-keyword->milliseconds current-date)
             (aux.t/date-keyword->milliseconds date)))))

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
  (let [change-percentage (price-change-percentage (or current-price price) price)
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
  [{:asset/keys          [id]
    current-historic    :asset.market-price/historic :as asset}
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
  [assets day-of-week & args]
  (-> assets (a.a/get-less-market-price-updated (merge {:day-of-week day-of-week} (when args (first args)))) first))

(defn less-updated-asset-v2
  [assets & args]
  (-> assets (a.a/get-less-market-price-updated (first args)) first))

(defn reset-retry-attempts
  ([]
   (if-let [assets (db.a/get-with-retry)]
     (reset-retry-attempts assets)
     (log/error "[MARKET-UPDATING] update-asset-market-price - can't get assets")))
  ([assets]
   (when (seq assets)
     (let [update-fn (fn [{:asset.market-price/keys [retry-attempts updated-at] :as asset}]
                       (if (and retry-attempts
                                updated-at
                                (aux.t/less-updated-than-target? 1 updated-at))
                         (dissoc asset :asset.market-price/retry-attempts)
                         asset))]
       (->> assets
            (map update-fn)
            db.a/upsert-bulk!)))))

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
    (db.a/upsert-bulk! updated-assets)))

(defn handle-retry-attempt
  [{:asset.market-price/keys [retry-attempts] :as less-updated-asset}
   assets]
  (log/info (str "Already retry [" (or retry-attempts 0) "], new attempt after 5sec"))
  (let [updated-assets (update-assets-retry-attempt assets less-updated-asset)]
    (db.a/upsert-bulk! updated-assets)
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
             :asset.market-price/keys [retry-attempts]} (less-updated-asset assets day-of-week (when args (first args)))]
     (try
       (log/info "[MARKET-UPDATING] Starting get asset price for " (:asset/ticket less-updated-asset))
       (let [market-last-price (get-market-price less-updated-asset (when args (first args)))
             updated-assets (update-assets assets less-updated-asset market-last-price)]
         (log/info "[MARKET-UPDATING] Success [" ticket "] " (:price market-last-price) " - " (:date market-last-price))
         (db.a/upsert-bulk! updated-assets))
       (catch Exception e
         #_(t.b/send-error-command bot e)
         #_(log/error e)
         (if (contains? (-> e ex-data :causes) :alpha-api-limit)
           (handle-alpha-api-limit-error assets less-updated-asset)
           (do (log/error (str (:fail log-colors)
                               (:asset/ticket less-updated-asset) " error in update-asset-market-price " e
                               (:end log-colors)))
               (if (< (or retry-attempts 0) 3)
                 (-> less-updated-asset
                     (handle-retry-attempt assets)
                     (update-asset-market-price day-of-week (when args (first args))))
                 (log/info (str "Retry limit archived")))))))
     (log/warn "[MARKET-UPDATING] No asset to be updated"))))


(defn update-asset-market-price-v2
  ([]
   (if-let [assets (db.a/get-all)]
     (update-asset-market-price assets)
     (log/error "[MARKET-UPDATING] update-asset-market-price - can't get assets")))
  ([assets & args]
   (if-let [{:asset/keys [ticket] :as less-updated-asset
             :asset.market-price/keys [retry-attempts]} (less-updated-asset-v2 assets (first args))]
     (try
       (log/info "[MARKET-UPDATING] Starting get asset price for " (:asset/ticket less-updated-asset))
       (let [market-last-price (get-market-price less-updated-asset (first args))
             updated-assets (update-assets assets less-updated-asset market-last-price)]
         (log/info "[MARKET-UPDATING] Success [" ticket "] " (:price market-last-price) " - " (:date market-last-price))
         (db.a/upsert-bulk! updated-assets))
       (catch Exception e
         #_(t.b/send-error-command bot e)
         #_(log/error e)
         (if (contains? (-> e ex-data :causes) :alpha-api-limit)
           (handle-alpha-api-limit-error assets less-updated-asset)
           (do (log/error (str (:fail log-colors)
                               (:asset/ticket less-updated-asset) " error in update-asset-market-price " e
                               (:end log-colors)))
               (if (< (or retry-attempts 0) 3)
                 (-> less-updated-asset
                     (handle-retry-attempt assets)
                     (update-asset-market-price (first args)))
                 (log/info (str "Retry limit archived")))))))
     (log/warn "[MARKET-UPDATING] No asset to be updated"))))

(defn update-asset-market-price-historic
  ([]
   (if-let [assets (db.a/get-all)]
     (update-asset-market-price-historic assets)
     (log/error "[GET STOCK HISTORIC] update-asset-market-price-historic - can't get assets")))
  ([assets]
   (update-asset-market-price-v2 assets {:with-historic true :ignore-timer true})))

(defn update-crypto-market-price
  ([]
   (if-let [assets (db.a/get-all)]
     (update-crypto-market-price assets)
     (log/error "[GET CRYPTO PRICE] update-crypto-market-price - can't get assets")))
  ([assets]
   (-> #(= :crypto (:asset/type %))
        (filter assets)
        (update-asset-market-price-v2 {:ignore-timer true}))))

(defn update-stock-market-price
  ([]
   (if-let [assets (db.a/get-all)]
     (update-stock-market-price assets)
     (log/error "[GET STOCK PRICE] update-stock-market-price - can't get assets")))
  ([assets]
   (-> #(= :crypto (:asset/type %))
        (remove assets)
        (update-asset-market-price-v2 {:ignore-timer true}))))

(comment

  (update-crypto-market-price)

  (->> #_(db.a/get-all)
       #_(filter #(= (:asset/ticket %) :AMAT))
       #_first
       (db.a/get-by-ticket :COIN)
       (get-market-price)
       )

  (-> (db.a/get-by-ticket :ABEV3)
      (get-stock-market-price true))

  (-> (db.a/get-all)
      (a.a/sort-by-updated-at)
      (clojure.pprint/print-table)
      #_(get-market-price)
      )

  (-> :LINK (db.a/get-by-ticket) list (update-asset-market-price-historic))

  (get-market-price)
  (update-asset-market-price)
  (def company-overview (io.http/get-company-overview "ABEV3.SA"))

  (clojure.pprint/pprint market-formated)

  (update-asset-market-price)

  (def as (db.a/get-all))

  (def ap (get-asset-market-price as))

  (->> (db.a/get-all)
       (filter #(= :STX (:asset/ticket %)))
       update-asset-market-price)

  ;--------------------
  (->> (less-updated-asset-v2 as nil)
       list
       (update-stock-market-price))

  (db.a/get-by-ticket :tots3)

  )
