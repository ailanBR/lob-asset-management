(ns lob-asset-management.models.asset-news
  (:require [schema.core :as s]))

(s/defschema AssetNews
             #:asset-news{:ticket                  s/Keyword
                          :name                    s/Str
                          :id                      s/Uuid
                          :txt                     s/Str
                          :datetime                s/Str
                          :href                    s/Str
                          (s/optional-key :asset-news/from)   s/Str
                          (s/optional-key :asset-news/status) s/Keyword})

(comment
  (let [a {:asset-news/ticket :COIN,
         :asset-news/name "COIN",
         :asset-news/id #uuid"8b1dd26d-1fbf-3eb1-bca9-070546cc3256",
         :asset-news/txt "Crypto: Long Queue for ETH Validators, VanEck Strengthens..",
         :asset-news/datetime "05/01/2024 17:08",
         :asset-news/href "https://br.advfn.com/noticias/IHMARKETNEWS/2024/artigo/92968252",
         :asset-news/from "FROM"}]
    (s/validate AssetNews a))

  )
