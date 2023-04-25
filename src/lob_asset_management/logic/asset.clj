(ns lob-asset-management.logic.asset
  (:require [schema.core :as s]))

(defn get-asset-by-name
  [assets name]
  (->> assets
       (filter #(= name (:asset/name %)))
       first))

;TODO: Create a specific ticket for CDB
(s/defn b3-ticket->asset-ticket  :- s/Keyword
  [xlsx-ticket :- s/Str]
  (-> xlsx-ticket
      (clojure.string/split #"-")
      first
      clojure.string/trim
      clojure.string/lower-case
      clojure.string/upper-case
      keyword))