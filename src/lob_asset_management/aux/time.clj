(ns lob-asset-management.aux.time
  (:require [java-time.api :as jt]
            [clj-time.coerce :as coerce]
            [clj-time.core :as t]))

(defn get-current-millis
  ([]
   (let [dt (jt/local-date-time)]
     (get-current-millis dt)))
  ([dt]
   (let [c-dt (coerce/to-date-time (str dt))]
     (.getMillis c-dt))))

(defn day-of-week
  [dt]
  (-> dt jt/day-of-week jt/value))

(defn clj-date->date-keyword
  [dt]
  (let [dt-str (str dt)
        dt-split (clojure.string/split dt-str #"T")]
    (keyword (first dt-split))))

(defn subtract-days
  "Receive a date keyword and the number of days to be subtracted
  date example :2023-06-21"
  [dt days]
  (let [c-dt (coerce/to-date-time (name dt))]
    (-> c-dt
        (t/minus (t/days days))
        clj-date->date-keyword)))
