(ns lob-asset-management.relevant
  (:require [lob-asset-management.io.file-in :refer [file->edn]]))

(def secrets (file->edn "./resources/secrets.edn"))

(def alpha-key (:alpha-vantage-key secrets))