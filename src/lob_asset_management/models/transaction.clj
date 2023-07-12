(ns lob-asset-management.models.transaction
  (:require [schema.core :as s]))

(s/defschema Exchange (s/enum :nu
                              :inter
                              :sprotify
                              :binance
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
                       :desdobro
                       :sell
                       :bonificaçãoemativo
                       :compraporliquides
                       :vencimento
                       :bonificaçãoemativos
                       :fraçãoemativos
                       :resgate
                       :incorporação
                       :waste
                       :atualização
                       :grupamento
                       :compra})

(s/defschema OperationType (apply s/enum operation-types))

(s/defschema Transaction
  {:transaction/id                s/Str
   :transaction/created-at        s/Str
   :transaction.asset/ticket      s/Keyword
   :transaction/average-price     BigDecimal
   :transaction/quantity          BigDecimal
   :transaction/exchange          Exchange
   :transaction/operation-type    s/Keyword
   :transaction/processed-at      s/Str})

