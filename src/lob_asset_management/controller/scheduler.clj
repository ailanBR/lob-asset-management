(ns lob-asset-management.controller.scheduler
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.controller.forex :as c.f]
            [lob-asset-management.controller.market :as c.m]
            [lob-asset-management.controller.portfolio :as c.p]
            [lob-asset-management.controller.telegram-bot :as t.bot :refer [bot]]
            [lob-asset-management.db.asset :as db.a]
            [sbocq.cronit :as cronit]
            [java-time.api :as jt]
            [java-time.format :as jf]
            [java-time.util :as ju]
            [lob-asset-management.aux.time :as aux.time]
            [lob-asset-management.aux.util :refer [log-colors]]))

;https://github.com/sbocq/cronit/blob/main/README.md

(def days-of-week #{:mon :tue :wed :thu :fri :sat :sun})

(def expressions {:every-minute {:minute [:* 1]}})

(defn new-cron
  ([cron-expression zoned-date]
   (cronit/init cron-expression zoned-date))
  ([cron-expression]
   (new-cron cron-expression (jt/zoned-date-time))))

(defn its-time!
  [{:keys [cron-exp fn name]} next]
  (log/info (str (:okgreen log-colors)
                     "[>] NOW!!!! " name
                     " N: " (-> next
                                :current
                                jt/zoned-date-time
                                jt/local-date-time
                                str)
                     " C: " (str (aux.time/current-date-time))
                     (:end log-colors)))
  (fn)
  (new-cron cron-exp))

(defn its-time-validate
  [{:keys [cron name] :as schedule}]
  (let [next (cronit/next cron)
        next-millis (-> next
                        :current
                        jt/zoned-date-time
                        jt/local-date-time
                        str
                        aux.time/get-millis)
        current-millis (aux.time/get-millis)]
    (if (> current-millis next-millis)
      (its-time! schedule next)
      (do (log/info (str (:okblue log-colors)
                         "[X] Not yet " name
                         " N: " (-> next
                                    :current
                                    jt/zoned-date-time
                                    jt/local-date-time
                                    str)
                         " C: " (str (aux.time/current-date-time))
                         (:end log-colors)))
          cron))))

(def get-stock-price
  {:name :get-stock-price
   :cron-exp {:minute [:* 4] :hour [:+ 11 12 13 14 15 16 17] :day-of-week [:+ :mon :tue :wed :thu :fri]}
   :cron     (new-cron {:minute [:* 4] :hour [:+ 11 12 13 14 15 16 17] :day-of-week [:+ :mon :tue :wed :thu :fri]})
   :times    :continuous
   :fn       #(do
                (c.m/update-asset-market-price)
                (c.p/update-portfolio-representation))
   :dependency #{:get-stock-hist}})

(def get-stock-hist
  {:name :get-stock-hist
   :cron-exp {:minute [:* 4] :hour [:+ 10 18]}
   :cron     (new-cron {:minute [:* 4] :hour [:+ 10 18] :day-of-week [:+ :mon :tue :wed :thu :fri]})
   :fn       #(do
                (c.m/update-asset-market-price-historic)
                (c.p/update-portfolio-representation))})

(def get-crypto-price
  {:name :get-crypto-price
   :cron-exp {:minute [:* 10]}
   :cron     (new-cron {:minute [:* 10]})
   :fn       #(do
                (c.m/update-crypto-market-price)
                (c.p/update-portfolio-representation))})

(def notify-price-highlight
  {:name     :notify-price-highlight
   :cron-exp {:minute [:+ 20] :hour [:+ 10 16 20] :day-of-week [:+ :mon :tue :wed :thu :fri]}
   :cron     (new-cron {:minute [:+ 20] :hour [:+ 10 16 20] :day-of-week [:+ :mon :tue :wed :thu :fri]})
   :fn       #(t.bot/send-command bot nil :daily)})

(def notify-portfolio-total
  {:name     :notify-portfolio-total
   :cron-exp {:minute [:+ 0] :hour [:+ 10 16 20] :day-of-week [:+ :mon :tue :wed :thu :fri]}
   :cron     (new-cron {:minute [:+ 0] :hour [:+ 10 16 20] :day-of-week [:+ :mon :tue :wed :thu :fri]})
   :fn       #(t.bot/send-command bot nil :total)})

(def check-telegram-new-message
  {:name     :check-telegram-new-message
   :cron-exp {:second [:* 5]}
   :cron     (new-cron {:second [:* 5]})
   :fn       #(t.bot/check-telegram-messages)})

(def forex-update
  {:name     :forex-update
   :cron-exp {:hour [:* 3] :day-of-week [:+ :mon :tue :wed :thu :fri]}
   :cron     (new-cron {:hour [:* 3] :day-of-week [:+ :mon :tue :wed :thu :fri]})
   :fn       #(c.f/update-usd-price)})

(def asset-backup
  {:name     :snapshot
   :cron-exp {:hour [:+ 7]}
   :cron     (new-cron {:hour [:+ 7]})
   :fn       #(db.a/snapshot)})

(defonce schedulers (atom [get-stock-price
                           get-stock-hist
                           get-crypto-price
                           notify-price-highlight
                           notify-portfolio-total
                           check-telegram-new-message
                           forex-update
                           asset-backup]))

(defn evaluate-schedulers
  []
  (log/info (str (:underline log-colors)
                 "**********Evaluating Start**********"
                 (:end log-colors)))
  (doseq [{:keys [name] :as s} @schedulers]
    #_(log/info (str (:bold log-colors)
                   "Evaluating " name
                   (:end log-colors)))
    (let [new-cron (its-time-validate s)
          s' (assoc s :cron new-cron)
          ss' (-> #(= (:name %) name) (remove @schedulers) (conj s'))]
      (reset! schedulers ss'))))

(defn poller
  [interval]
  (let [run-forest-run (atom true)]
    (future
      (try
        (while @run-forest-run
          ;(log/info "[Schedulers poller running" (str (jt/local-date-time)) "]")
          (evaluate-schedulers)
          (log/info "[Schedulers poller next " (str (jt/plus
                                                      (jt/local-date-time)
                                                      (jt/millis interval))) "]")
          (Thread/sleep interval))
        (catch Exception e
          (println (:fail log-colors) "Error in scheduler poller: " e (:end log-colors)))))
    (fn [] (reset! run-forest-run false))))

(defn scheduler-show
  [name]
  (if-let [{:keys [cron-exp] :as s} (->> @schedulers (filter #(= (% :name) name)) first)]
    (do (clojure.pprint/pprint s)
      (cronit/show cron-exp))
    (println "NOT FOUND " name)))

(comment
  ;Only saturday - 5 minutes interval
  (cronit/show {:hour [:* 24] :day-of-week [:+ :sat]}
             {:date (str (jt/zoned-date-time)) :context 2})
  )
