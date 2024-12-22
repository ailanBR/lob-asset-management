(ns lob-asset-management.models.transaction
  (:require [schema.core :as s]))

(s/defschema Exchange (s/enum :nu
                              :inter
                              :sprotify
                              :binance
                              :vest
                              :other))

(def operation-types #{:buy
                       :transferência
                       :dividend
                       :JCP
                       :income
                       :solicitaçãodesubscrição
                       :cessãodedireitos-solicitada
                       :direitodesubscrição
                       :cessãodedireitos
                       :direitosdesubscrição-excercído
                       :direitosdesubscrição-nãoexercido
                       :recibodesubscrição
                       :split
                       :sell
                       :bonificaçãoemativo
                       :compraporliquides
                       :vencimento
                       :bonus
                       :fraçãoemativos
                       :redemption
                       :incorporação
                       :waste
                       :atualização
                       :reverse-split
                       :compra
                       :subscription})

(s/defschema OperationType (apply s/enum operation-types))

(s/defschema Currency (apply s/enum #{:BRL :UST}))

(s/defschema Factor {:operator    (apply s/enum #{"/" "*" "+"})
                     :denominator BigDecimal})

(s/defschema Transaction
  {:transaction/id                               s/Uuid
   :transaction/created-at                       s/Int
   :transaction.asset/ticket                     s/Keyword
   :transaction/average-price                    BigDecimal
   :transaction/quantity                         BigDecimal
   :transaction/exchange                         Exchange
   :transaction/operation-type                   s/Keyword
   :transaction/processed-at                     s/Str
   :transaction/currency                         Currency
   :transaction/operation-total                  BigDecimal
   (s/optional-key :transaction/incorporated-by) s/Keyword
   (s/optional-key :transaction/factor)          Factor})

