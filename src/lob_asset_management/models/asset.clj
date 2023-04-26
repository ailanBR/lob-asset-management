(ns lob-asset-management.models.asset
  (:require [schema.core :as s]))

(s/defschema Asset
  {:asset/id            s/Uuid
   :asset/name          s/Str
   :asset/ticket        s/Keyword
   :asset/category      [s/Keyword]
   :asset/last-price    BigDecimal
   :asset/type          s/Keyword})
