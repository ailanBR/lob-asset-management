(ns lob-asset-management.models.asset
  (:require [schema.core :as s]))

(def crypto-list #{:BTC :ETH :CAKE :BNB :ALGO :LUNA :FANTOM :BUSD :MATIC :USDT :STX :LINK})
(def fii-list #{:ALZR11 :HGBS11 :KNRI11 :RECR11})
(def etf-list #{:DEFI11})
(def bdr-list #{:NUBR33 :INBR31})

(s/defschema Asset
  {:asset/id         s/Uuid
   :asset/name       s/Str
   :asset/ticket     s/Keyword
   :asset/category   [s/Keyword]
   (s/optional-key :asset/last-price) BigDecimal
   :asset/type s/Keyword
   (s/optional-key :asset/tax-number) s/Str})
