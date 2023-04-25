(ns lob-asset-management.models.transaction
  (:require [schema.core :as s]
            [lob-asset-management.models.asset :as m.asset]))

(s/defschema Exchange (s/enum :nu
                              :inter
                              :sprotify
                              :binance))

(s/defschema Transaction
  {:transaction/id                s/Uuid
   :transaction/created-at        s/Str
   :transaction/aset              m.asset/Asset
   :transaction/asset-id          s/Uuid
   :transaction/asset-ticket      s/Keyword
   :transaction/average-price       BigDecimal
   :transaction/quantity          BigDecimal
   :transaction/exchange          Exchange
   :transaction/type              s/Keyword
   :transaction/operation-type    s/Keyword})


