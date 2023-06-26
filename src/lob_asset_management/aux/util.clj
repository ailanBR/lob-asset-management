(ns lob-asset-management.aux.util)

(defn assoc-if
  ([map new-args]
   (reduce #(-> %1 (cond-> (val %2) (assoc (key %2) (val %2)))) map new-args))
  ([map key value]
   (-> map
       (cond-> value (assoc key value)))))

(defn str-space->keyword-underline [m]
  (zipmap (map #(keyword (clojure.string/replace (name %) " " "_")) (keys m))
          (vals m)))

(defn- remove-close-parenthesis [m]
  (zipmap (map #(keyword (clojure.string/replace (name %) ")" "")) (keys m))
          (vals m)))

(defn- remove-open-parenthesis [m]
  (zipmap (map #(keyword (clojure.string/replace (name %) "(" "")) (keys m))
          (vals m)))

(defn remove-keyword-parenthesis
  [m]
  (-> m
      remove-close-parenthesis
      remove-open-parenthesis))