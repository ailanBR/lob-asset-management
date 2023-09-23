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
  (if-let [stored-news (db.an/get-by-ticket (-> received-news first :asset-news/ticket))]
    (let [stored-ids (->> stored-news (map :asset-news/id) set)
          new-ones (remove #(contains? stored-ids (:asset-news/id %)) received-news)]
      new-ones)
    received-news))

(defn update-news
  [{:asset/keys [ticket name]} news]
  (when news
    (when-let [asset-news (->> news
                               (a.a/external-news->internal ticket name)
                               asset-news-new-one
                               (remove #(not (:asset-news/id %))))]
      (when (not (empty? asset-news))
        (t.b/asset-news-message asset-news bot)
        #_(db.an/upsert-bulk! asset-news)))))

(defn get-stock-market-price
  [{:asset.market-price/keys [historic] :as asset} & args]
  (let [get-historic? (or (-> args first :with-historic) historic)]
    (if-let [{:keys [news] :as market-info}                 ;(-> asset a.a/in-ticket->out-ticket io.http/get-daily-adjusted-prices)
             (if get-historic?
               (io.http/advfn-data-extraction-br asset)
               ;(-> asset a.wde/in-ticket->out-ticket io.http/advfn-data-extraction)
               (-> asset a.a/in-ticket->out-ticket io.http/get-daily-adjusted-prices))]
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
       (log/info "[MARKET-UPDATING] Stating get asset price for " (:asset/ticket less-updated-asset))
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
  (get-stock-market-price {:asset/ticket :ABEV3,
                           :asset/type :stockBR
                           :asset.market-price/historic
                           {:2023-05-02 14.2600M,
                            :2023-08-18 14.3600M,
                            :2023-03-23 13.7800M}})

  (def company-overview (io.http/get-company-overview "ABEV3.SA"))

  (clojure.pprint/pprint market-formated)

  (update-asset-market-price)

  (def as (db.a/get-all))

  (def ap (get-asset-market-price as))

  (->> (db.a/get-all)
       (filter #(= :STX (:asset/ticket %)))
       update-asset-market-price)
  )
