(ns lob-asset-management.core
  (:require [clojure.tools.cli :as t.cli]
            [clojure.tools.logging :as log]
            [java-time.api :as t]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.aux.xtdb :refer [db-node]]
            [lob-asset-management.controller.forex :as c.f]
            [lob-asset-management.controller.market :as c.m]
            [lob-asset-management.controller.process-file :as c.p-f]
            [lob-asset-management.controller.portfolio :as c.p]
            [lob-asset-management.controller.release :as c.r]
            [lob-asset-management.controller.scheduler :as c.s]
            [lob-asset-management.controller.telegram-bot :as t.bot :refer [bot]]
            [lob-asset-management.db.asset :as db.a]
            [lob-asset-management.db.forex :as db.f]
            [lob-asset-management.db.portfolio :as db.p]
            [lob-asset-management.db.transaction :as db.t]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.relevant :refer [config config-prod]]
            [mount.core :as mount]))

(defn start [env]
  "Default env = :dev

  v2 => mount functions to be used by environment"
  (mount/start #'lob-asset-management.relevant/config
               #'lob-asset-management.relevant/alpha-key
               #'lob-asset-management.relevant/telegram-key
               #'lob-asset-management.relevant/telegram-personal-chat
               #'lob-asset-management.controller.telegram-bot/bot)
  (when (= :prod env)
    (mount/start #'lob-asset-management.aux.xtdb/db-node)
    (mount/start-with {#'lob-asset-management.relevant/config config-prod})))

(defn stop []
  (mount/stop #'lob-asset-management.relevant/config
              #'lob-asset-management.relevant/alpha-key
              #'lob-asset-management.relevant/telegram-key
              #'lob-asset-management.relevant/telegram-personal-chat
              #'lob-asset-management.controller.telegram-bot/bot
              #'lob-asset-management.aux.xtdb/db-node))

;(start :dev)                                                     ;for develop purpose
;(stop)

(def cli-options
  [["-y" "--year Year" "Year of the release"
    :default 2022
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 2021 % 2024) "Must be a number between 2021 and 2023"]]
   ["-d" "--[no-]daemon" "Daemonize the process" :default true]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["This is my program. There are many like it, but this one is mine."
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  start    Start the get market price and update portfolio"
        "  read     Read the movements files"
        "  release  [send the year as -y parameter] Generate a new release"
        "  telegram [send the message as -m parameter] Send a message to telegram"
        ""
        "Please refer to the manual page for more information."]
       (clojure.string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with an error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (t.cli/parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      (and (= 1 (count arguments))
           (#{"start" "read" "release" "telegram"} (first arguments)))
      {:action (first arguments) :options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn get-market-info
  [forex-usd stock-window current-time]
  (let [assets (db.a/get-all)
        portfolio (db.p/get-all)
        current-hour (.getHour current-time)
        day-of-week (aux.t/day-of-week current-time)]
    (c.m/reset-retry-attempts assets)
    (if (contains? stock-window current-hour)
      (when (c.m/update-asset-market-price assets day-of-week)
        (c.p/update-portfolio-representation portfolio forex-usd))
      (when (c.m/update-crypto-market-price assets)
        (c.p/update-portfolio-representation portfolio forex-usd)))))

(defn start-processing
  [stock-window interval]
  (let [forex-usd (db.f/get-all)
        update-target-hour 3
        current-time (t/local-date-time)]
    (t.bot/check-telegram-messages interval current-time)
    (c.p-f/backup-cleanup :asset)                           ;FIXME: Didn't work in the shell execution
    (if (c.f/less-updated-than-target forex-usd update-target-hour)
      (c.f/update-usd-price forex-usd update-target-hour)
      (get-market-info forex-usd stock-window current-time))))

(defn poller [f-name f interval window]
  (let [run-forest-run (atom true)]
    (future
      (try
        (while @run-forest-run
          ;(log/info "[Poller running" (str (t/local-date-time)) "]")
          (if (contains? window (.getHour (t/local-date-time)))
            (f)
            (log/info "[Poller " (str f-name "-" (t/local-date-time)) "] Out of the configured window hour " window))
          (log/info "[Poller next " (str f-name "-" (t/plus (t/local-date-time)
                                                            (t/millis interval))) "]")
          (Thread/sleep interval))
        (catch Exception e
          (println "Error in " f-name " poller: " e))))
    (fn [] (reset! run-forest-run false))))

(defn -main [& args]
  (start :prod)
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "start" (let [interval 3000
                      stop-loop (c.s/poller interval)
                      #_(poller "Main"
                              #(start-processing #{11 12 13 14 15 16 17 18 19 20 21 22 23} interval)
                              13000
                              #{7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 00 01})]
                  (db.a/snapshot)
                  (db.p/snapshot)
                  (db.t/snapshot)
                  (println "Press enter to stop...")
                  (read-line)
                  (future-cancel (stop-loop))
                  @(stop-loop))
        "read"    (c.p-f/process-folders)
        "release" (c.r/irpf-release (:year options))))))

(comment
  (schema.core/set-fn-validation! true)

  (c.p-f/delete-all-files)
  (c.p-f/process-folders)
  ;=========================================

  (clojure.pprint/print-table
    [:portfolio/ticket :portfolio/quantity]
    (->> (db.p/get-all)
         ;(filter #(contains? #{:nu :inter} (first (:portfolio/exchanges %))))
         ;(filter #(contains? #{:sproutfy} (first (:portfolio/exchanges %))))
         ;(filter #(= :crypto (:portfolio/category %)))
         ;(filter #(or (contains? (:portfolio/exchanges %) :nu)
         ;             (contains? (:portfolio/exchanges %) :inter)))
         (sort-by :portfolio/ticket)))

  (filter #(= :SMTO3 (:portfolio/ticket %)) (db.p/get-all))

  (clojure.pprint/print-table
    [:transaction/id :transaction/created-at :transaction/operation-type :transaction/quantity :transaction/average-price :transaction/operation-total]
    (->> (lob-asset-management.db.transaction/get-all)
         ;(filter #(= :fraçãoemativos (:transaction/operation-type %)))
         (filter #(or (= :SULA3 (:transaction.asset/ticket %))))
         ;(remove #(contains?
         ;           #{:sell :JCP :income :dividend :bonificaçãoemativos
         ;             :fraçãoemativos :transferência :waste :incorporation}
         ;           (:transaction/operation-type %)))
         ;(filter #(or (= :SQIA3 (:transaction.asset/ticket %))
         ;             (= :EVTC31 (:transaction.asset/ticket %))
         ;             (= :E9TC11 (:transaction.asset/ticket %))))
         ;(filter #(contains? #{:sproutfy} (:transaction/exchange %)))
         ;(sort-by :transaction/exchange)
         ;(sort-by :transaction.asset/ticket)
         (sort-by :transaction/created-at)
         ))

  (->> (lob-asset-management.db.transaction/get-all)
       ;(filter #(= :fraçãoemativos (:transaction/operation-type %)))
       ;(filter #(or (= :SULA11 (:transaction.asset/ticket %))))
       ;(remove #(contains?
       ;           #{:sell :JCP :income :dividend :bonificaçãoemativos
       ;             :fraçãoemativos :transferência :waste :incorporation}
       ;           (:transaction/operation-type %)))
       (filter #(or (= :SQIA3 (:transaction.asset/ticket %))
                      (= :EVTC31 (:transaction.asset/ticket %))))
       ;(filter #(contains? #{:sproutfy} (:transaction/exchange %)))
       ;(sort-by :transaction/exchange)
       ;(sort-by :transaction.asset/ticket)
       (sort-by :transaction/created-at)
       ;(reduce #(+ %1 (:transaction/quantity %2)) 0)
       )
  ;=========================================
  (def in (lob-asset-management.relevant/incorporation-movements))

  (c.p-f/process-movement in)

  (def m (io.f-in/get-file-by-entity :metric))
  (def tm (filter #(and (= "https://www.alphavantage.co/query" (-> % :endpoint :url))
                        (= :2023-07-17 (aux.t/clj-date->date-keyword (:when %))))
                  (:metric/api-call m)))


  (def c (atom 0))
  (map #(let [f (-> % :endpoint :params :query-params :function)
              s (-> % :endpoint :params :query-params :symbol)]
          (swap! c inc)
          {:x (str "[" @c "]" f " - " (or s ""))}) tm)

  (c.m/update-crypto-market-price)
  (c.p/update-portfolio-representation (db.p/get-all) (db.f/get-all)))
