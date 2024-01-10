(ns lob-asset-management.aux.time
  (:require [java-time.api :as jt]
            [java-time.format :as jf]
            ))

(def br-date-time-format "dd/MM/yyyy' 'HH:mm")
(def timestamp-format "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS")

(defn get-millis
  "e.g. 2023-09-18T21:38:52.259569
  28/03/2023 13:21 => fmt = \"dd/MM/yyyy' 'HH:mm\""
  ([]
   (let [dt (jt/local-date-time)]
     (get-millis dt)))
  ([dt]
   (let [dt' (if (= 19 (count (str dt))) (str dt ".") dt)
         date-time-string (->> dt'
                               str
                               (format "%-29s"))
         dt-fmt (clojure.string/replace date-time-string " " "0")]
     (get-millis dt-fmt timestamp-format)))
  ([dt format]
   (let [fmt (jt/formatter format)
         c-dt (jt/zoned-date-time fmt dt "America/Sao_Paulo")]
     (-> c-dt .toInstant .toEpochMilli))))

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
    (let [c-dt (jt/local-date (name dt))]
      (-> c-dt
          (jt/minus (jt/days days))
          clj-date->date-keyword))))

(defn date-keyword->clj-date
  [dt]
  (when dt
    (jt/local-date (name dt))))

(defn current-date-time
  []
  (jt/local-date-time))

(defn current-datetime->str
  []
  (str (current-date-time)))

(defn current-date->keyword
  []
  (clj-date->date-keyword (current-date-time)))

(defn current-datetime-minus-hh
  [target-hours]
  (jt/minus (jt/local-date-time) (jt/hours target-hours)))

(defn less-updated-than-target?
  [target-hours updated-at]
  (or (nil? updated-at)
      (< updated-at
         (get-millis (current-datetime-minus-hh target-hours)))))

(defn- month-str->number
  [str-month]
  (condp = (clojure.string/lower-case str-month)
    "jan" "01"
    "feb" "02"
    "fev" "02"
    "mar" "03"
    "abr" "04"
    "apr" "04"
    "mai" "05"
    "may" "05"
    "jun" "06"
    "jul" "07"
    "ago" "08"
    "aug" "08"
    "set" "09"
    "sep" "09"
    "out" "10"
    "oct" "10"
    "nov" "11"
    "dez" "12"
    "dec" "12"))

(defn- month-number->str
  [month-number]
  (condp = month-number
    1  "jan"
    2  "feb"
    3  "mar"
    4  "apr"
    5  "may"
    6  "jun"
    7  "jul"
    8  "aug"
    9  "sep"
    10 "oct"
    11 "nov"
    12 "dec"))

(defn str-date->str-timestamp
  "e.g. Sep 01 2023"
  [str-date]
  (let [splited (clojure.string/split str-date #" ")
        day (second splited)
        month (month-str->number (first splited))
        year (last splited)]
    (str year "-" month "-" day "T00:00:00.000000000")))

(defn str-date->date-keyword
  "e.g. Sep 01 2023"
  [str-date]
  (clj-date->date-keyword (str-date->str-timestamp str-date)))


(defn str-br-date->str-timestamp
  "e.g. 11 Set 2023"
  [str-date]
  (let [splited (clojure.string/split str-date #" ")
        day (first splited)
        month (month-str->number (second splited))
        year (last splited)]
    (str year "-" month "-" day "T00:00:00.000000000")))

(defn str-br-date->date-keyword
  "e.g. 11 Set 2023"
  [str-date]
  (clj-date->date-keyword (str-br-date->str-timestamp str-date)))

(defn date-keyword->milliseconds
  "e.g. :2023-09-01"
  [dt-keyword]
  (let [splited (-> dt-keyword name (clojure.string/split #"-"))
        day (last splited)
        month (month-number->str (-> splited second Integer/parseInt))
        year (first splited)]
    (-> (clojure.string/join " " [month day year])
        str-date->str-timestamp
        get-millis)))

(defn milliseconds->date-keyword
  [milliseconds]
  (-> milliseconds
      jt/instant
      str
      (clojure.string/split #"T")
      first
      keyword))

(defmacro timed [expr]                                      ;TODO: USE THIS
  (let [sym (= (type expr) clojure.lang.Symbol)]
    `(let [start# (. System (nanoTime))
           return# ~expr
           res# (if ~sym
                  (resolve '~expr)
                  (resolve (first '~expr)))]
       (prn (str "Timed "
                 (:name (meta res#))
                 ": " (/ (double (- (. System (nanoTime)) start#)) 1000000.0) " msecs"))
       return#)))
