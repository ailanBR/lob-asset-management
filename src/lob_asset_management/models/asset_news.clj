(ns lob-asset-management.models.asset-news
  (:require [schema.core :as s]))

(s/defschema AssetNews
             #:asset-news{:ticket                  s/Keyword
                          :name                    s/Str
                          :id                      s/Str
                          :txt                     s/Str
                          :datetime                s/Str
                          :href                    s/Str
                          (s/optional-key :status) s/Keyword})
