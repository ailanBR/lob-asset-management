(ns lob-asset-management.adapter.asset
  (:require [clojure.tools.logging :as log]
            [schema.core :as s]
            [lob-asset-management.models.asset :as m.a]
            [lob-asset-management.logic.asset :as l.a]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.relevant :refer [asset-more-info]]
            [java-time.api :as t])
  (:import (java.util UUID)))

(defn allowed-ticket-get-market-info?
  [ticket]
  (let [{:keys [alpha-vantage-allowed?]} (get asset-more-info ticket)]
    alpha-vantage-allowed?))

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
           (not (= "." try-ticket->number))
           (-> try-ticket->number Integer/parseInt)) :stockBR
      :else :stockEUA)))

(defn ticket->categories
  [ticket]
  (let [{:keys [category]} (get asset-more-info ticket)]
    (or category
        [:unknown])))

(defn get-part-string
  [c start end]
  (-> c
      (clojure.string/split #"")
      (subvec start end)
      (clojure.string/join)))

(defn format-br-tax
  [br-tax]
  (let [digits (-> br-tax
                   str
                   (clojure.string/replace "." "")
                   (clojure.string/replace "/" "")
                   (clojure.string/replace "-" ""))]
    (str (get-part-string digits 0 2) "."
         (get-part-string digits 2 5) "."
         (get-part-string digits 5 8) "/"
         (get-part-string digits 8 12) "-"
         (get-part-string digits 12 14))))

(s/defn movement-ticket->asset-ticket  :- s/Keyword
  [xlsx-ticket :- s/Str]
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
        keyword)))

(s/defn movements->asset :- m.a/Asset
  [{:keys [product]}]
  ;(println "[ASSET] ROW " (str product))
  (let [ticket (movement-ticket->asset-ticket product)
        {:keys [name tax-number category]} (get asset-more-info ticket)
        asset-type (ticket->asset-type ticket)]
    {:asset/id         (UUID/randomUUID)
     :asset/name       (or name (str product))
     :asset/ticket     ticket
     :asset/category   (or category [:unknown])
     :asset/type       (ticket->asset-type ticket)
     :asset/tax-number (when (and (not (empty? tax-number))
                                  (contains? #{:fii :stockBR} asset-type)) (format-br-tax tax-number))}))

(defn remove-already-exist-asset
  [db-data assets]
  (remove #(l.a/already-exist-asset? (:asset/ticket %) db-data) assets))

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
   (filter-allowed-type assets #{:stockBR :fii :stockEUA :crypto}))
  ([assets allowed-types]
   (filter #(contains? allowed-types (:asset/type %)) assets)))

(defn filter-allowed-ticket
  [assets]
  (filter #(allowed-ticket-get-market-info? (:asset/ticket %)) assets))

(defn filter-less-updated-than-target?
  [target-hours assets]
  (filter #(l.a/less-updated-than-target? target-hours (:asset.market-price/updated-at %))
          assets))

(defn remove-limit-attempts
  [assets]
  (remove (fn [{:asset.market-price/keys [retry-attempts]}]
            (> (or retry-attempts 0) 2)) assets))

(defn get-less-market-price-updated
  ([assets]
   (get-less-market-price-updated assets 1))
  ([assets quantity]
   (get-less-market-price-updated assets quantity 1))
  ([assets quantity min-updated-hours]
   (let [filter-assets (->> assets
                            filter-allowed-type
                            filter-allowed-ticket
                            remove-disabled-ticket
                            remove-limit-attempts
                            (sort-by :asset.market-price/updated-at)
                            (filter-less-updated-than-target? min-updated-hours))]
     (or (take quantity filter-assets)
         nil))))

(comment
  (def assets-file (io.f-in/get-file-by-entity :asset))

  (clojure.pprint/print-table assets-file)

  (get-less-market-price-updated assets-file 1 1)
  )