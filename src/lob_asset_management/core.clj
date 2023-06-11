(ns lob-asset-management.core
  (:require [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.controller.forex :as c.f]
            [lob-asset-management.controller.market :as c.m]
            [lob-asset-management.controller.process-file :as c.p]
            [lob-asset-management.controller.release :as c.r]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.relevant :refer [configurations]]
            [java-time.api :as t]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [parse-opts]]
            [lob-asset-management.io.storage :as io.s]))

(def cli-options
  [;; First three strings describe a short-option, long-option with optional
   ;; example argument description, and a description. All three are optional
   ;; and positional.
   ["-y" "--year PORT" "Year of the release"
    :default 2022
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 2021 % 2024) "Must be a number between 2021 and 2023"]]
   ;["-H" "--hostname HOST" "Remote host"
   ; :default (InetAddress/getByName "localhost")
   ; ;; Specify a string to output in the default column in the options summary
   ; ;; if the default value's string representation is very ugly
   ; :default-desc "localhost"
   ; :parse-fn #(InetAddress/getByName %)]
   ;; If no required argument description is given, the option is assumed to
   ;; be a boolean option defaulting to nil
   ;[nil "--detach" "Detach from controlling process"]
   ;["-v" nil "Verbosity level; may be specified multiple times to increase value"
   ; ;; If no long-option is specified, an option :id must be given
   ; :id :verbosity
   ; :default 0
   ; ;; Use :update-fn to create non-idempotent options (:default is applied first)
   ; :update-fn inc]
   ;["-f" "--file NAME" "File names to read"
   ; :multi true ; use :update-fn to combine multiple instance of -f/--file
   ; :default []
   ; ;; with :multi true, the :update-fn is passed both the existing parsed
   ; ;; value(s) and the new parsed value from each option
   ; :update-fn conj]
   ;; A boolean option that can explicitly be set to false
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
        "  release  Generate a new release"
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
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      (and (= 1 (count arguments))
           (#{"start" "read" "release"} (first arguments)))
      {:action (first arguments) :options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn start-processing
  []
  (let [forex-usd (io.f-in/get-file-by-entity :forex-usd)
        update-target-hour 1]
    (if (c.f/less-updated-than-target forex-usd update-target-hour)
      (c.f/update-usd-price)
      (when (c.m/update-asset-market-price)
        (-> (a.p/update-portfolio)
            (io.f-out/upsert))))))

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
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "start"  (let [stop-loop (poller "Main"
                                         #(start-processing)
                                         15000
                                         #{9 12 13 16 17 18 19 20})]
                   (println "Press enter to stop...")
                   (read-line)
                   (future-cancel (stop-loop))
                   @(stop-loop))
        "read"    (c.p/process-folders)
        "release" (c.r/irpf-release (:year options))))))

(comment
  (schema.core/set-fn-validation! true)

  (c.p/delete-all-files)
  (c.p/process-folders)
  ;(c.p/process-b3-folder)
  ;(c.p/process-b3-folder-only-new)

  (c.r/irpf-release 2022)

  (-> (io.f-in/get-file-by-entity :transaction)
      a.p/transactions->portfolio
      io.f-out/upsert )

  (clojure.pprint/print-table [:portfolio/ticket :portfolio/quantity] (io.f-in/get-file-by-entity :portfolio))
  ;;Market data poller

  (c.m/update-asset-market-price)
  (defn my-function []
    (println "Hello, world! [" (str (t/local-date-time)) "]"))


  (c.f/update-usd-price)
  (def get-market-price-poller (poller "GetMarketPrice"
                                       #(c.m/update-asset-market-price)
                                       15000
                                       #{8 9 10 12 14 16 18 19 20 21 22 23}))
  (get-market-price-poller)

  (def get-usd-price (poller "GetUSDprice"
                             #(c.f/update-usd-price)
                             60000
                             #{8 10 12 14 16 18}))
  (get-usd-price)

  (-> (a.p/update-portfolio) (io.f-out/upsert))
  (def update-portfolio (poller "UpdatePortfolio"
                                #(-> (a.p/update-portfolio) (io.f-out/upsert))
                                30000
                                #{8 9 10 12 14 16 18 19 20 21 22 23}))
  (update-portfolio)
  ;INTERVAL CONFIG POLLING ALPHA VANTAGE
  ;LIMIT : 5 API requests per minute and 500 requests per day
  ;15000 => 15sec => 4 Request per minute
  ; 13 Min to do 50 requests
  ;18000 => 18sec => 3 Request per minute
  ; 15 Min to do 50 requests
  ;20000 => 20sec => 3 Request per minute
  ; 17 Min to do 50 requests
  ;- Create an internal
  ;  Only request in business hours and market is open
  ;  seg ~ sex / 10:00	16:55
  ; Per hours => 8 Group Requests (50) => 400 Requests
  ; #(10 12 14 16 18) => 5 Group Requests (50) => 250 Requests
  ; #(10 11 12 13 14 15 16 17 18) => 9 Group Requests (50) => 450 Requests
  ;
  ;V1 Choice /Only BR data
  ; INTERVAL => 15seg
  ; WHEN => #(10 11 12 13 14 15 16 17 18)
  (clojure.pprint/print-table
    [:transaction/created-at  :transaction/operation-type :transaction/quantity :transaction/average-price :transaction/operation-total]
    (->> (io.f-in/get-file-by-entity :transaction)
         (filter #(contains? #{:sproutfy} (:transaction/exchange %)))
         (filter #(= :AAPL (:transaction.asset/ticket %)))
         (sort-by :transaction/created-at)
         (sort-by :transaction.asset/ticket)
         ))

  (let [app-t (->> (io.f-in/get-file-by-entity :transaction)
                   (filter #(contains? #{:sproutfy} (:transaction/exchange %)))
                   (filter #(= :AAPL (:transaction.asset/ticket %)))
                   (sort-by :transaction/created-at)
                   (sort-by :transaction.asset/ticket))]
    (reduce #(+ %1 (:transaction/quantity %2)) 0M app-t))
  )