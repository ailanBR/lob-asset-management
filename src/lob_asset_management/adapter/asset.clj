(ns lob-asset-management.adapter.asset
  (:require [clojure.tools.logging :as log]
            [schema.core :as s]
            [lob-asset-management.models.asset :as m.a]
            [lob-asset-management.logic.asset :as l.a]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.relevant :refer [asset-more-info]]
            [java-time.api :as jt])
  (:import (java.util UUID)))

(defn allowed-get-market-info-tickets?
  [ticket]
  (let [{:keys [alpha-vantage-allowed?]} (get asset-more-info ticket)]
    alpha-vantage-allowed?))

(s/defn ticket->asset-type :- s/Keyword
  [ticket :- s/Keyword]
  (let [try-ticket->number (-> ticket
                               name
                               (clojure.string/replace #"([A-Z])" "")
                               (clojure.string/replace #"." ""))
        ticket-number? (empty? try-ticket->number)]
    (cond
      (or (clojure.string/includes? (name ticket) "TESOURO")
          (clojure.string/includes? (name ticket) "CDB")) :fixed-income
      (contains? m.a/fii-list ticket) :fii
      (contains? m.a/crypto-list ticket) :crypto
      (contains? m.a/etf-list ticket) :etf
      (contains? m.a/bdr-list ticket) :bdr
      (and (not ticket-number?)
           (-> try-ticket->number Integer/parseInt)) :stockBR
      :else :stockEUA)))

(defn ticket->categories
  [ticket]
  (let [{:keys [category]} (get asset-more-info ticket)]
    (or category [:unknown])))

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

(s/defn movements->asset :- m.a/Asset
  [{:keys [product]}]
  ;(println "[ASSET] ROW " (str product))
  (let [ticket (l.a/movement-ticket->asset-ticket product)
        {:keys [name tax-number category]} (get asset-more-info ticket)
        asset-type (ticket->asset-type ticket)]
    {:asset/id         (UUID/randomUUID)
     :asset/name       (or name (str product))
     :asset/ticket     ticket
     :asset/category   (or category [:unknown])
     :asset/type       (ticket->asset-type ticket)
     :asset/tax-number (when (and (not (empty? tax-number))
                                  (contains? #{:fii :stockBR} asset-type)) (format-br-tax tax-number))}))

(defn already-read-asset
  [{:asset/keys [ticket]} db-data]
  (if (empty? db-data)
    true
    (let [db-data-tickets (->> db-data (map :asset/ticket) set)]
      (not (contains? db-data-tickets ticket)))))

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
                         (filter #(already-read-asset % db-data))
                         (concat (or db-data []))
                         (sort-by :asset/name))]
     (log/info "[ASSET] Concluded adapter... "
               "read assets [" (count mov-assets) "] "
               "result [" (count new-assets) "]")
     new-assets)))

(defn disabled-ticket-get-market-price
  [assets]
  (filter (fn [{:asset/keys [ticket]}]
            (not (contains? #{:INHF12} ticket))) assets))

(defn allowed-type-get-market-price
  [assets]
  (filter (fn [{:asset/keys [type ticket]}]
            (and (contains? #{:stockBR :fii} type)
                 (allowed-get-market-info-tickets? ticket))) assets))

(defn less-updated-than-target
  [asset target-hours]
  (< (:asset.market-price/updated-at asset)
     (aux.t/get-current-millis
       (jt/minus (jt/local-date-time) (jt/hours target-hours)))))

(defn get-less-market-price-updated
  ([assets]
   (get-less-market-price-updated assets 1))
  ([assets quantity]
   (get-less-market-price-updated assets quantity 1))
  ([assets quantity min-updated-hours]
   (let [filter-assets (->> assets
                            allowed-type-get-market-price
                            disabled-ticket-get-market-price
                            (sort-by :asset.market-price/updated-at)
                            (filter #(or (nil? (:asset.market-price/updated-at %))
                                         (less-updated-than-target % min-updated-hours))))]
     (or (take quantity filter-assets)
         nil))))

(comment
  (def assets-file (io.f-in/get-file-by-entity :asset))

  (clojure.pprint/print-table assets-file)

  (def b3-mov (lob-asset-management.io.file-in/read-xlsx-by-file-name "movimentacao-20220101-20220630.xlsx"))

  (map :asset/ticket assets-file)

  (clojure.pprint/print-table (movements->assets b3-mov assets-file))

  (def l (first (get-less-market-price-updated assets-file 1 10)))

  (first (get-less-market-price-updated assets-file 1 5))

  (get-less-market-price-updated assets-file 1 3)
  )