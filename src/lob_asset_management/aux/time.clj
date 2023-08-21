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

(defn clj-date->date-time-str
  [dt]
  (let [dt-str (str dt)
        dt-split (clojure.string/split dt-str #"T")
        hr-split (-> dt-split second (clojure.string/split #"\.") first)]
    (str (first dt-split) " " hr-split)))

(defn subtract-days
  "Receive a date keyword and the number of days to be subtracted
  date example :2023-06-21"
  [dt days]
  (when dt
    (let [c-dt (coerce/to-date-time (name dt))]
      (-> c-dt
          (t/minus (t/days days))
          clj-date->date-keyword))))

(defn current-date-time
  []
  (jt/local-date-time))

(defn current-datetime->str
  []
  (str (current-date-time)))

(defn current-date->keyword
  []
  (clj-date->date-keyword (current-date-time)))

(defn less-updated-than-target?
  [target-hours updated-at]
  (or (nil? updated-at)
      (< updated-at
         (get-current-millis (jt/minus (jt/local-date-time) (jt/hours target-hours))))))
