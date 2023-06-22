(ns lob-asset-management.aux.time
  (:require [java-time.api :as jt]
            [clj-time.coerce :as coerce]
            [clj-time.core :as t]
            [java-time.format :as f]))

(defn get-current-millis
  ([]
   (let [dt (jt/local-date-time)]
     (get-current-millis dt)))
  ([dt]
   (let [c-dt (coerce/to-date-time (str dt))]
     (.getMillis c-dt))))

(defn day-of-week
  [dt]
  (cond
    (jt/monday? dt) 1
    (jt/tuesday? dt) 2
    (jt/wednesday? dt) 3
    (jt/thursday? dt) 4
    (jt/friday? dt) 5
    (jt/saturday? dt) 6
    (jt/sunday? dt) 7))

(defn clj-date->date-keyword
  [dt]
  (let [dt-str (str dt)
        dt-split (clojure.string/split dt-str #"T")]
    (keyword (first dt-split))))

(defn subtract-days
  [dt days]
  (let [c-dt (coerce/to-date-time (name dt))]
    (-> c-dt
        (t/minus (t/days days))
        clj-date->date-keyword)))
