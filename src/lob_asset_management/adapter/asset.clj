(ns lob-asset-management.adapter.asset
  (:require [schema.core :as s]
            [lob-asset-management.models.asset :as m.a]
            [lob-asset-management.logic.asset :as l.a]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.aux.time :as aux.t]
            [java-time.api :as jt]
            [clj-time.coerce :as coerce]
            ;[clj-time.core :as t]
            ;[clj-time.local :as local]
            ;[clj-time.format :as t.f]
            )
  (:import (java.util UUID)))

(s/defn ticket->categories :- [s/Keyword]
  [ticket :- s/Keyword]
  (condp = ticket
    :BBAS3 [:finance]
    :ALZR11 [:fii :hybrid]
    :HGBS11 [:fii :shopping]
    :INBR31 [:finance]
    :INHF12 [:finance]
    :CDB [:private-bond]
    :KNRI11 [:fii :hybrid]
    :RECR11 [:fii :paper]
    :RECR12 [:fii :paper]
    :ABEV3 [:food]
    :B3SA3 [:finance]
    :BIDI11 [:finance]
    :BPAC3 [:finance]
    :CAML3 [:food]
    :EQTL3 [:energy]
    :FLRY3 [:health]
    :HAPV3 [:health]
    :H3CO11 [:health]
    :H3CO3 [:health]
    :LOGN3 [:transport]
    :NEOE3 [:energy]
    :PDTC3 [:ti]
    :RAIL3 [:industry]
    :RENT3 [:transport]
    :ROMI3 [:industry]
    :SMTO3 [:food]
    :SQIA3 [:ti]
    :SULA11 [:health]
    :SULA3 [:health]
    :TOTS3 [:ti]
    :AAPL [:ti]
    :ABT [:health]
    :AMZN [:ti]
    :BLK [:finance]
    :BLOK [:crypto]
    :BRK.B [:finance]
    :CAN [:crypto]
    :CNBS [:cannabis]
    :COIN [:crypto]
    :GILD [:health]
    :GOOGL [:ti]
    :IIPR [:cannabis]
    :INDA [:world]
    :ISRG [:health]
    :ITW [:industry]
    :JPM [:finance]
    :MCHI [:world]
    :MDT [:health]
    :GNDI3 [:health]
    :WEGE3 [:industry]
    :MSFT [:ti]
    :SE [:ti]
    :SKYY [:ti]
    :TSLA [:transport]
    :ALZR11 [:fii :shopping]
    :DEFI11 [:crypto]
    :HGBS11 [:fii :shopping]
    :KNRI11 [:fii :hybrid]
    :OIBR3 [:telecom]
    :OIBR1 [:telecom]
    :ARE [:fii :tijolo]
    :EQIX [:fii :tijolo :ti]
    :GWH [:energy]
    :BTC [:crypto]
    :ETH [:crypto]
    :BNB [:crypto]
    :ALGO [:crypto]
    :LUNA [:crypto]
    :CAKE [:crypto]
    :FANTOM [:crypto]
    [:unknown]
    ;(throw (AssertionError. (str "Ticket->Categories => " ticket)))
    ))

(s/defn ticket->asset-type :- s/Keyword
  [ticket :- s/Keyword]
  (let [try-ticket->number (-> ticket
                               str
                               (clojure.string/replace #"([A-Z])" "")
                               (clojure.string/replace ":" ""))
        ticket-number? (empty? try-ticket->number)]
    (cond
      (and (not ticket-number?)
           (-> try-ticket->number Integer/parseInt (= 11) true?))  :fii ;;Invalde filter Ex.: SULA11
      (and (not ticket-number?)
           (-> try-ticket->number Integer/parseInt)) :stockBR
      (contains? #{:BTC :ETH :CAKE} ticket) :crypto
      :else :stockEUA)))

(s/defn movement->asset :- m.a/Asset
  [{:keys [product]}]
  (let [ticket (l.a/b3-ticket->asset-ticket product)]
    {:asset/id         (UUID/randomUUID)
     :asset/name       product
     :asset/ticket     ticket
     :asset/category   (ticket->categories ticket)
     :asset/type       (ticket->asset-type ticket)}))

(defn movements->assets
  ([mov]
   (movements->assets mov ()))
  ([mov db-data]
   (println "Processing adapter asset...")
   (let [assets (->> mov
                     (map movement->asset)
                     (filter #(not (contains? % db-data)))
                     (concat db-data)
                     (group-by :asset/ticket)
                     (map #(-> % val first))
                     (sort-by :asset/name))]
     (println "Concluded adapter asset...")
     assets)))

(defn get-asset-by-name
  [assets name]
  (->> assets
       (filter #(= name (:asset/name %)))
       first))

(defn disabled-ticket-get-market-price
  [assets]
  (filter (fn [{:asset/keys [ticket]}]
            (not (contains? #{:INHF12} ticket))) assets))

(defn allowed-type-get-market-price
  [assets]
  (filter (fn [{:asset/keys [type]}]
            (contains? #{:stockBR :fii} type)) assets))

(defn get-less-market-price-updated
  ([assets]
   (get-less-market-price-updated assets 1))
  ([assets quantity]
   (get-less-market-price-updated assets quantity 1))
  ([assets quantity min-updated-hours]
   (->> assets
        allowed-type-get-market-price
        disabled-ticket-get-market-price
        (sort-by :asset.market-price/updated-at)
        (filter #(or (nil? :asset.market-price/updated-at)
                     (< (:asset.market-price/updated-at %)
                        (aux.t/get-current-millis (jt/minus (jt/local-date-time) (jt/hours min-updated-hours))))))
        (take quantity))))

(comment

  (def assets-file (io.f-in/get-file-by-entity :asset))

  (def l (first (get-less-market-price-updated assets-file 1 10)))

  (first (get-less-market-price-updated assets-file 1 5))

  (get-less-market-price-updated assets-file 1 3)



  )