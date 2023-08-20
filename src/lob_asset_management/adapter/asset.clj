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
      :USDT :tether)))

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

(s/defn movements->asset :- m.a/Asset
  [{:keys [product]}]
  (let [ticket (movement-ticket->asset-ticket product)
        {:keys [name tax-number category]} (get asset-more-info ticket)
        asset-type (ticket->asset-type ticket)]
    {:asset/id         (UUID/randomUUID)
     :asset/name       (or name (str product))
     :asset/ticket     ticket
     :asset/category   (or category [:unknown])
     :asset/type       (ticket->asset-type ticket)
     :asset/tax-number (when (and (not (empty? tax-number))
                                  (contains? #{:fii :stockBR} asset-type)) (aux.u/format-br-tax tax-number))}))

(defn remove-already-exist-asset
  [assets-keep asset-filtered]
  (remove #(l.a/already-exist? (:asset/ticket %) assets-keep) asset-filtered))

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
                         (concat (or db-data []))
                         (sort-by :asset/name))]
     (log/info "[ASSET] Concluded adapter... "
               "read assets [" (count mov-assets) "] "
               "result [" (count new-assets) "]")
     new-assets)))


(defn remove-disabled-ticket
  [assets]
  (remove (fn [{:asset/keys [ticket]}]
            (contains? #{:INHF12 :USDT :SULA3 :SULA11} ticket)) assets))

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
  [target-hours assets]
  (filter #(aux.t/less-updated-than-target? target-hours (:asset.market-price/updated-at %))
          assets))

(defn remove-limit-attempts
  [assets]
  (remove (fn [{:asset.market-price/keys [retry-attempts]}]
            (> (or retry-attempts 0) 2)) assets))

(defn get-less-updated-config
  [args]
  (assoc-if {:quantity          1
             :min-updated-hours 1
             :type              #{:stockBR :fii :stockEUA :crypto}
             :day-of-week       1}
            args))

(defn get-less-market-price-updated
  "Return the less updated asset

   Receive an option map with filter parameters
     :quantity -> How many assets will be returned  [default => 1]
     :min-updated-hours -> The less updated asset than [default => 1]
     :type -> Only asset types from this list [default => #{:stockBR :fii :stockEUA :crypto}]
     :day-of-week -> On weekends get only crypto prices [default => 1 (Monday)]"
  [assets & args]
  {:pre [(boolean assets)]}
  (let [{:keys [quantity min-updated-hours
                type day-of-week]} (get-less-updated-config (first args))
        type' (if (> day-of-week 5) #{:crypto} type)
        filter-assets (->> assets
                           (filter-allowed-type type')
                           filter-allowed-ticket
                           remove-disabled-ticket
                           remove-limit-attempts
                           (sort-by :asset.market-price/updated-at)
                           (filter-less-updated-than-target? min-updated-hours))]
    (or (take quantity filter-assets) nil)))

(comment
  (def assets-file (lob-asset-management.db.transaction/get-file-by-entity :asset))

  (clojure.pprint/print-table assets-file)

  (get-less-market-price-updated assets-file {:min-updated-hours 1
                                              :quantity 1
                                              :day-of-week 1})
  )
