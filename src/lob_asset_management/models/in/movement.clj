(ns lob-asset-management.models.in.movement
  (:require [schema.core :as s]))

(s/defschema movement {:type             s/Str
                       :transaction-date s/Str
                       :movement-type    s/Str
                       :product          s/Str
                       :exchange         s/Str
                       :quantity         s/Str
                       :unit-price       s/Str
                       :operation-total  s/Str
                       (s/optional-key :currency) s/Str})

(comment

  (let [m {:type             "Credito"
           :transaction-date "27/04/2021"
           :movement-type    "Transferência - Liquidação"
           :product          "AAPL"
           :exchange         "Sproutfy"
           :quantity         "0,12202170"
           :unit-price       "$155,71"
           :operation-total  "$19,00"
           :currency "a"}]
    (s/validate movement m))

  )

