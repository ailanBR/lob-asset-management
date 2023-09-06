(ns lob-asset-management.core
  (:require [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.controller.forex :as c.f]
            [lob-asset-management.controller.market :as c.m]
            [lob-asset-management.controller.process-file :as c.p-f]
            [lob-asset-management.controller.portfolio :as c.p]
            [lob-asset-management.controller.release :as c.r]
            [lob-asset-management.db.asset :as db.a]
            [lob-asset-management.db.forex :as db.f]
            [lob-asset-management.db.portfolio :as db.p]
            [lob-asset-management.io.file-in :as io.f-in]
            [mount.core :as mount]
            [java-time.api :as t]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :as t.cli]
            [lob-asset-management.controller.telegram-bot :as t.bot :refer [bot]]))

(defn start []
  (mount/start #'lob-asset-management.relevant/config
               #'lob-asset-management.relevant/alpha-key
               #'lob-asset-management.relevant/telegram-key
               #'lob-asset-management.relevant/telegram-personal-chat
               #'lob-asset-management.controller.telegram-bot/bot))

(start)                                                     ;for develop purpose

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

(defonce update-id (atom nil))

(defn set-id!
  "Sets the update id to process next as the passed in `id`."
  [id]
  (reset! update-id id))

(defn check-telegram-messages
  [interval time]
  (let [updates (t.bot/pull-updates bot @update-id)
        messages (:result updates)]
    (doseq [msg messages]
      (t.bot/handle-msg bot msg)
      (-> msg :update_id inc set-id!))
    (t.bot/auto-message bot time interval)))

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
    (check-telegram-messages interval current-time)
    (c.p-f/backup-cleanup :asset)
    (if (c.f/less-updated-than-target forex-usd update-target-hour)
      (c.f/update-usd-price forex-usd update-target-hour)
      (get-market-info forex-usd stock-window current-time))))

(defn poller [f-name f interval window]
  (let [run-forest-run (atom true)]
    (future
      (try
        (while @run-forest-run
          ;(println "[Poller running" (str (t/local-date-time)) "]")
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
  (start)
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "start" (let [interval 13000
                      stop-loop (poller "Main"
                                        #(start-processing #{16 19 20 21 22 23} interval)
                                        13000
                                        #{7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 00 01})]
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
    [:portfolio/ticket :portfolio/quantity :portfolio/total-last-value]
    (->> (db.p/get-all)
         ;(filter #(or (contains? (:portfolio/exchanges %) :nu)
         ;             (contains? (:portfolio/exchanges %) :inter)))
         (sort-by :portfolio/ticket)))

  (clojure.pprint/print-table
    [:transaction/created-at :transaction/operation-type :transaction/quantity]
    (->> (lob-asset-management.db.transaction/get-all)
         ;(filter #(= :fraçãoemativos (:transaction/operation-type %)))
         (filter #(or (= :BTC (:transaction.asset/ticket %))))
         ;(remove #(contains?
         ;           #{:buy :sell :JCP :income :dividend :waste :grupamento}
         ;           (:transaction/operation-type %)))
         ;(filter #(or (= :ROMI3 (:transaction.asset/ticket %))))
         ;(filter #(contains? #{:sproutfy} (:transaction/exchange %)))
         ;(sort-by :transaction/exchange)
         (sort-by :transaction.asset/ticket)
         ;(sort-by :transaction.asset/ticket)
         ))
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
