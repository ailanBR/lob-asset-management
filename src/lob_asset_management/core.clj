(ns lob-asset-management.core
  (:require [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.controller.process-file :as c.p]
            [lob-asset-management.controller.market :as c.m]
            [lob-asset-management.controller.release :as c.r]
            [java-time.api :as t]
            ;[clj-time.core :as t]
            [clojure.tools.logging :as log]))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defn my-function []
  (println "Hello, world! [" (str (t/local-date-time)) "]"))

(defn poller [f interval]
  (let [run-forest-run (atom true)
        window-hours #{10 12 14 16 18}]
    (future
      (try
        (while @run-forest-run
          ;(println "[Poller running" (str (t/local-date-time)) "]")
          (if (contains? window-hours (.getHour (t/local-date-time)))
            (f)
            (log/info "[Poller " (str (t/local-date-time)) "] Out of the configured window hour " window-hours))
          (log/info "[Poller next " (str (t/plus (t/local-date-time)
                                                (t/millis interval))) "]")
          (Thread/sleep interval))
        (catch Exception e
          (println "Error in poller: " e))))
    (fn [] (reset! run-forest-run false))))

(defn -main
  ;FIXME : Command don't executed
  ; NOP :- 1. Maybe is the root path, the function don't find the files
  ; 2. Don't have access to read/write in command line
  [& args]
  (when (nil? args)
    (println "SELECT A COMMAND")
    (println " p -> Process all files in the release folder")
    (println " d -> Delete all files in the release folder")
    (println " pn -> Process new files in the release folder")
    (println " s -> Start poller process"))
  (when-let [read (or (first args) (read-line))]
    (let [command (-> read clojure.string/lower-case keyword)]
      (condp = command
        :p (do (println "PROCESSING FILE FOLDERS")
               (c.p/process-b3-folder))
        :d (do (println "DELETING ALL FILES IN FOLDER")
               (c.p/delete-all-files))
        :s (do (println "STATING POOLER")
               (let [stop-loop (poller #(c.m/update-asset-market-price) 15000)]
                 (println "Press enter to stop...")
                 (read-line)
                 (future-cancel (stop-loop))
                 @(stop-loop)))
        (println "INVALID COMMAND"))))
  (println "FINISH"))

(comment
  (schema.core/set-fn-validation! true)

  (c.p/delete-all-files)
  (c.p/process-folders)
  ;(c.p/process-b3-folder)
  ;(c.p/process-b3-folder-only-new)

  (c.r/irpf-release 2022)

  (clojure.pprint/print-table [:portfolio/ticket :portfolio/percentage] (io.f-in/get-file-by-entity :portfolio))
  ;;Market data poller

  (c.m/update-asset-market-price)
  (defn my-function []
    (println "Hello, world! [" (str (t/local-date-time)) "]"))

  (def get-market-price-poller (poller #(c.m/update-asset-market-price) 15000))
  (get-market-price-poller)

  (let [stop-loop (poller my-function 3000)]
    (println "Press enter to stop.")
    (read-line)
    (future-cancel (stop-loop))
    @(stop-loop))

  (let [stop-loop (poller my-function 15000)]
    (Thread/sleep 60000)
    (stop-loop))

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
    [:transaction/created-at :transaction.asset/ticket :transaction/operation-type :transaction/quantity :transaction/average-price :transaction/operation-total]
    (->> (io.f-in/get-file-by-entity :transaction)
         (filter #(contains? #{:sproutfy} (:transaction/exchange %)))
         (sort-by :transaction/created-at)
         (sort-by :transaction.asset/ticket)))
  )