(ns lob-asset-management.aux.money)

(defn safe-big
  [value]
  (or value 0M))

(defn safe-dob
  [value]
  (or value 0.0))