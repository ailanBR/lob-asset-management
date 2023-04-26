(ns lob-asset-management.models.transaction
  (:require [schema.core :as s]))

(s/defschema Exchange (s/enum :nu
                              :inter
                              :sprotify
                              :binance
                              :other))

(s/defschema Transaction
  {:transaction/id                s/Str
   :transaction/created-at        s/Str
   :transaction.asset/ticket      s/Keyword
   :transaction/average-price     BigDecimal
   :transaction/quantity          BigDecimal
   :transaction/exchange          Exchange
   :transaction/operation-type    s/Keyword
   :transaction/processed-at      s/Str})

