(ns lob-asset-management.logic.asset
  (:require [schema.core :as s]))

(defn get-asset-by-name
  [assets name]
  (->> assets
       (filter #(= name (:asset/name %)))
       first))

;TODO: Create a specific ticket for CDB
(s/defn movement-ticket->asset-ticket  :- s/Keyword
  [xlsx-ticket :- s/Str]
  (let [xlsx-ticket-split-first (-> xlsx-ticket
                                    (clojure.string/split #"-")
                                    first
                                    clojure.string/trimr)
        cdb-ticket? (= "CDB" xlsx-ticket-split-first)
        xlsx-ticket' (if cdb-ticket? xlsx-ticket xlsx-ticket-split-first)]
    (-> xlsx-ticket'
        (clojure.string/replace #" " "-")
        (clojure.string/replace #"S/A" "SA")
        (clojure.string/replace #"---" "-")
        (clojure.string/replace #"--" "-")
        clojure.string/lower-case
        clojure.string/upper-case
        keyword)))