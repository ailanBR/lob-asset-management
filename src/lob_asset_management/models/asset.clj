(ns lob-asset-management.models.asset
  (:require [schema.core :as s]))

(def crypto-list #{:BTC :ETH :CAKE :BNB :ALGO :LUNA :FANTOM})

(def fii-list #{:ALZR11 :HGBS11 :KNRI11 :RECR11})

(s/defschema Asset
  {:asset/id            s/Uuid
   :asset/name          s/Str
   :asset/ticket        s/Keyword
   :asset/category      [s/Keyword]
   :asset/last-price    BigDecimal
   :asset/type          s/Keyword})
