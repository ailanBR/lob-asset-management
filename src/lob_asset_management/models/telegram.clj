(ns lob-asset-management.models.telegram
  (:require [schema.core :as s]))

(s/defschema TelegramMessage
  {:telegram/id         s/Uuid
   :telegram/message    s/Str
   :telegram/created-at s/Str
   :telegram/category   s/Str
   :telegram/active     s/Bool})
