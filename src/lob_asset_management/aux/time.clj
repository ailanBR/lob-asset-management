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

(defn subtract-days
  [dt days]
  (let [c-dt (coerce/to-date-time (name dt))]
    (t/minus c-dt (t/days days))))

(defn clj-date->date-keyword
  [dt]
  (let [dt-str (str dt)
        dt-split (clojure.string/split dt-str #"T")]
    (keyword (first dt-split))))