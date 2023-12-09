(ns lob-asset-management.controller.scheduler
  (:require [sbocq.cronit :as cronit]
            [java-time.api :as jt]
            [java-time.format :as jf]
            [java-time.util :as ju]
            [lob-asset-management.aux.time :as aux.time]))

(def days-of-week #{:mon :tue :wed :thu :fri :sat :sun})

(def expressions {:every-minute {:minute [:* 1]}})

(defn new-cron
  ([cron-expression zoned-date]
   (cronit/init cron-expression zoned-date))
  ([cron-expression]
   (new-cron cron-expression (jt/zoned-date-time))))

(defn its-time-validate
  ;TODO : Add callback fn when its time
  [cron cron-expression]
  (let [next (cronit/next cron)
        next-millis (-> next
                        :current
                        jt/zoned-date-time
                        jt/local-date-time
                        str
                        aux.time/get-millis)
        current-millis (aux.time/get-millis)]
    (if (> current-millis next-millis)
      (do (println "NOW!!!!")
          (new-cron cron-expression))
      (do (println "Not yet")
          cron))))

(comment
  ;Get current datetime zoned
  (str (jt/zoned-date-time))

  ;Only saturday - 5 minutes interval
  (cronit/show {:hour [:* 24] :day-of-week [:+ :sat]}
             {:date (str (jt/zoned-date-time)) :context 2})

  ;Only saturday - 5 minutes interval
  (->> #_(jt/zoned-date-time)
       #_(cronit/init {:hour [:* 24] :day-of-week [:+ :mon :tue :wed :thu :fri :sat :sun]})
       init
       cron/next
       :current
       str
       (jt/zoned-date-time)
       (jt/local-date-time)
       str
       ;(jf/format "yyyy-MM-dd hh:mm:ss")
      )

  ;NOTES
  ;context => number of samples


  (-> c  :current str)
  (cronit/show (:every-minute expressions)
               {:date (str (jt/zoned-date-time)) :context 2})

  ;USAGE
  ;1. Create the cron
  (def c (new-cron (:every-minute expressions)))


  (its-time-validate c (:every-minute expressions))

  ;DEFINE A MAP WITH THE SCHEDULERS FOLLOWING WITH THE RESPECTIVE CRON EXPRESSSION
  (def schedulers {:get-stock-price {:cron-exp {:minute [:* 1]}
                                     :cron     (new-cron {:minute [:* 1]})}})

  (def as (atom {:cron-exp {:minute [:* 1]}
                 :cron     (new-cron {:minute [:* 1]})}))
  (def counter (atom 0))

  (while (< @counter 30)
    (println "[ " @counter " ]" (str (jt/local-date-time)))
    (swap! counter inc)
    (let [cron-exp (:cron-exp @as)
          cron (:cron @as)
          new-cron' (its-time-validate cron cron-exp)]
      (swap! as assoc :cron new-cron'))
    (Thread/sleep 1000))

  )
