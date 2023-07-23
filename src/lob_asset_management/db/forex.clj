(ns lob-asset-management.db.forex
  (:require [lob-asset-management.io.file-in :as io.f-in]))

(defn get-all
  []
  (io.f-in/get-file-by-entity :forex-usd))