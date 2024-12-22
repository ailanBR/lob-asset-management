(ns lob-asset-management.adapter.asset
  (:require [clojure.tools.logging :as log]
            [schema.core :as s]
            [lob-asset-management.models.asset :as m.a]
            [lob-asset-management.logic.asset :as l.a]
            [lob-asset-management.relevant :refer [asset-more-info]]
            [lob-asset-management.aux.util :refer [assoc-if]]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.aux.util :as aux.u])
  (:import (java.util UUID)))

(defn in-ticket->out-ticket
  [{:asset/keys [ticket type]}]
  (let [asset-name (name ticket)]
    (if (or (= type :stockBR) (= type :fii))
      (str asset-name ".SA")
      asset-name)))

(defn in-ticket->out-crypto-id
  [{:asset/keys [ticket type]}]
  (when (= type :crypto)
    (condp = ticket
      :BTC :bitcoin
      :ETH :ethereum
      :ALGO :algorand
      :BNB :binancecoin
      :CAKE :pancakeswap-token
      :LUNA :terra-luna-2
      :FANTOM :fantom
      :BUSD :binance-usd
      :MATIC :matic-network
      :STX :blockstack
      :USDT :tether
      :LINK :chainlink)))

(s/defn ticket->asset-type :- s/Keyword
  [ticket :- s/Keyword]
  (let [try-ticket->number (-> ticket
                               name
                               (clojure.string/replace #"([A-Z])" ""))
        ticket-number? (empty? try-ticket->number)]
    (cond
      (or (clojure.string/includes? (name ticket) "TESOURO")
          (clojure.string/includes? (name ticket) "CDB")) :fixed-income
      (contains? m.a/fii-list ticket) :fii
      (contains? m.a/crypto-list ticket) :crypto
      (contains? m.a/etf-list ticket) :etf
      (contains? m.a/bdr-list ticket) :bdr
      (and (not ticket-number?)
           (not= "." try-ticket->number)
           (-> try-ticket->number Integer/parseInt)) :stockBR
      :else :stockEUA)))

(defn ticket->categories
  [ticket]
  (let [{:keys [category]} (get asset-more-info ticket)]
    (or category
        [:unknown])))

(defn movement-ticket->asset-ticket
  [xlsx-ticket ]
  (when xlsx-ticket
    (let [xlsx-ticket-split-first (-> xlsx-ticket
                                      (clojure.string/split #"-")
                                      first
                                      clojure.string/trimr)
          cdb-ticket? (= "CDB" xlsx-ticket-split-first)
          xlsx-ticket' (if cdb-ticket? xlsx-ticket xlsx-ticket-split-first)]
      (-> xlsx-ticket'
          (clojure.string/replace #" " "-")
          (clojure.string/replace #"S/A" "SA")
          (clojure.string/replace #"---" "-")
          (clojure.string/replace #"--" "-")
          clojure.string/lower-case
          clojure.string/upper-case
          keyword))))

(s/defn ticket->asset-id :- s/Uuid
  [ticket :- s/Keyword]
  (-> ticket name (str "asset/") aux.u/string->uuid))

(s/defn movements->asset :- m.a/Asset
  [{:keys [product]}]
  (let [ticket (movement-ticket->asset-ticket product)
        {:keys [tax-number category]
         name' :name} (get asset-more-info ticket)
        asset-type (ticket->asset-type ticket)]
    {:asset/id         (ticket->asset-id ticket)
     :asset/name       (or name' (str product))
     :asset/ticket     ticket
     :asset/category   (or category [:unknown])
     :asset/type       (ticket->asset-type ticket)
     :asset/tax-number (when (and (not (empty? tax-number))
                                  (contains? #{:fii :stockBR} asset-type)) (aux.u/format-br-tax tax-number))}))

(defn remove-already-exist-asset
  [asset-filtered assets-keep]
  (remove #(l.a/already-exist? (:asset/ticket %) asset-filtered) assets-keep))

(defn update-assets
  [assets db-assets]
  (->> []
       (or db-assets)
       (remove-already-exist-asset assets)
       (concat (or assets []))
       (sort-by :asset/name)))

(defn movements->assets
  ([mov]
   (movements->assets mov ()))
  ([mov db-data]
   (log/info "[ASSET] Processing adapter... current assets [" (count db-data) "]")
   (let [mov-assets (->> mov
                         (map movements->asset)
                         (group-by :asset/ticket)
                         (map #(-> % val first)))
         new-assets (->> mov-assets
                         (remove-already-exist-asset db-data)
                         (concat (or db-data []))           ;TODO: Maybe can be removed (Considering only new assets)
                         (sort-by :asset/name))]
     (log/info "[ASSET] Concluded adapter... "
               "read assets [" (count mov-assets) "] "
               "result [" (count new-assets) "]")
     new-assets)))

(defn remove-disabled-ticket
  [assets]
  (remove (fn [{:asset/keys [ticket]}]
            (contains? #{:INHF12 :USDT :SULA3 :SULA11 :SQIA3 :EVTC31 :STOC31} ticket)) assets))

(defn filter-allowed-type
  ([assets]
   (filter-allowed-type #{:stockBR :fii :stockEUA :crypto} assets))
  ([allowed-types assets]
   (filter #(contains? allowed-types (:asset/type %)) assets)))

(defn allowed-ticket-get-market-info?
  [ticket]
  (let [{:keys [alpha-vantage-allowed?]} (get asset-more-info ticket)]
    alpha-vantage-allowed?))

(defn filter-allowed-ticket
  [assets]
  (filter #(allowed-ticket-get-market-info? (:asset/ticket %)) assets))

(defn filter-less-updated-than-target?                      ;TODO: Receive milliseconds instead target-hours
  [target-hours ignore-timer assets]
  (if (not ignore-timer)
    (filter #(aux.t/less-updated-than-target? target-hours (:asset.market-price/updated-at %))
            assets)
    assets))

(defn remove-limit-attempts
  [assets]
  (remove (fn [{:asset.market-price/keys [retry-attempts]}]
            (> (or retry-attempts 0) 2)) assets))

(defn get-less-updated-config
  [args]
  (assoc-if {:quantity          1
             :min-updated-hours 1
             :type              #{:stockBR :fii :stockEUA :crypto}}
            args))

(defn get-less-market-price-updated                         ;TODO: Move to logic
  "Return the less updated asset

   Receive an option map with filter parameters
     :quantity -> How many assets will be returned  [default => 1]
     :min-updated-hours -> The less updated asset than [default => 1]
     :type -> Only asset types from this list [default => #{:stockBR :fii :stockEUA :crypto}]
     *DEPRECATED* :day-of-week -> On weekends get only crypto prices [default => 1 (Monday)]  "
  [assets & args]
  {:pre [(boolean assets)]}
  (let [{:keys [quantity min-updated-hours type ignore-timer]} (get-less-updated-config (first args))
        filter-assets (->> assets
                           (filter-allowed-type type)
                           filter-allowed-ticket
                           remove-disabled-ticket
                           remove-limit-attempts
                           (sort-by :asset.market-price/updated-at)
                           (filter-less-updated-than-target? min-updated-hours ignore-timer))]
    (or (take quantity filter-assets) nil)))

(defn sort-by-updated-at
  [assets]
  (->> assets
       filter-allowed-ticket
       remove-disabled-ticket
       remove-limit-attempts
       (sort-by :asset.market-price/updated-at)
       (map (fn [{:asset/keys [ticket]
                  :asset.market-price/keys [updated-at price-date retry-attempts]}]
              {:ticket ticket
               :price-date price-date
               :updated-at updated-at
               :retry-attempts (or retry-attempts 0)}))))

(defn external-news->internal
  [ticket name news]
  (map (fn [{:keys [txt datetime href from]}]
         {:asset-news/ticket   ticket
          :asset-news/name     name
          :asset-news/id       (aux.u/string->uuid href)
          :asset-news/txt      txt
          :asset-news/datetime datetime
          :asset-news/href     href
          :asset-news/from      from}) news))

(comment
  (def assets-file (lob-asset-management.db.transaction/get-file-by-entity :asset))

  (clojure.pprint/print-table assets-file)

  (get-less-market-price-updated assets-file {:min-updated-hours 1
                                              :quantity 1
                                              :day-of-week 1})
  )
