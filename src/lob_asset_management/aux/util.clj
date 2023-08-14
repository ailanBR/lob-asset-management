(ns lob-asset-management.aux.util)

(defn assoc-if
  ([map new-args]
   (reduce #(cond-> %1 (val %2) (assoc (key %2) (val %2))) map new-args))
  ([map key value & args]
   (if (even? (count args))
     (let [updated-map (assoc-if map {key value})]
       (if args
         (let [pair-args (atom (-> args vec))
               temp-map (atom updated-map)]
           (while (> (count @pair-args) 0)
             (when (or (first (rest @pair-args)) (not (empty? (first (rest @pair-args)))))
               (swap! temp-map #(assoc-if % {(first @pair-args) (first (rest @pair-args))})))
             (swap! pair-args #(-> % rest rest vec)))
           @temp-map)
         updated-map))
     (throw (ex-info :invalid-args "this function needs a pair key value")))))

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

(defn abs
  [num]
  (max num (- num)))

(defn get-part-string
  [c start end]
  (-> c
      (clojure.string/split #"")
      (subvec start end)
      (clojure.string/join)))

(defn format-br-tax
  [br-tax]
  (let [digits (-> br-tax
                   str
                   (clojure.string/replace "." "")
                   (clojure.string/replace "/" "")
                   (clojure.string/replace "-" ""))]
    (str (get-part-string digits 0 2) "."
         (get-part-string digits 2 5) "."
         (get-part-string digits 5 8) "/"
         (get-part-string digits 8 12) "-"
         (get-part-string digits 12 14))))
