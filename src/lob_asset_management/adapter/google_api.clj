(ns lob-asset-management.adapter.google-api
  (:require [schema.core :as s]
            [lob-asset-management.models.in.movement :as model.movement]))

(s/defn get-by-index
  [i :- s/Int
   columns-config]
  (->> (sort columns-config)
       (map-indexed list)
       (filter #(= (first %) i))
       first
       second
       second))

(defn values->indexed-map
  ([l]
   (values->indexed-map l 0 {}))
  ([l i res]
   (if (seq l)
     (->>
       (assoc res i (first l))
       (values->indexed-map (rest l) (+ i 1)))
     res)))

(s/defn spread-sheet-out->in :- [model.movement/movement]
  [response
   columns-config]
  (->> (map (fn [k] {(keyword (key k)) (val k)}) response)
       (reduce conj)
       :values
       (map (fn [l]
              (->> (values->indexed-map l)
                   (map (fn [ln]
                          {(get-by-index (first ln) columns-config) (second ln)}))
                   (reduce merge))))))
