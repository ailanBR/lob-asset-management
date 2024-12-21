(ns lob-asset-management.adapter.google-api)

(defn get-by-index
  [i columns-config]
  (->> (sort columns-config)
       (map-indexed list)
       (filter #(= (first %) i))
       first
       second
       second))

(defn values->indexed-map
  [l i res]
  (if (seq l)
    (->>
      (assoc res i (first l))
      (values->indexed-map (rest l) (+ i 1)))
    res))

(defn spread-sheet-out->in
  [response columns-config]
  (->> (map (fn [k] {(keyword (key k)) (val k)}) response)
       (reduce conj)
       :values
       (map (fn [l]
              (->> (values->indexed-map l 0 {})
                   (map (fn [ln]
                          {(get-by-index (first ln) columns-config) (second ln)}))
                   (reduce merge))))))
