(ns lob-asset-management.aux.util)

(defn assoc-if
  ([map new-args]
   (reduce #(-> %1 (cond-> (val %2) (assoc (key %2) (val %2)))) map new-args))
  ([map key value]
   (-> map
       (cond-> value (assoc key value)))))