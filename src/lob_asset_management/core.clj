(ns lob-asset-management.core
  (:require [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.io.file-in :as io.i]
            [lob-asset-management.controller.process-file :as c.p]
            [lob-asset-management.controller.market :as c.m]
            [java-time.api :as t]
            ;[clj-time.core :as t]
            ))

;FIXME : Include log lib https://mattjquinn.com/2014/log4j2-clojure/ to avoid error

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
          (if (contains? window-hours
                         (.getHour (t/local-date-time)))
            (f)
            (println "[Poller " (str (t/local-date-time)) "] Out of the configured window hour " window-hours))
          (println "[Poller next " (str (t/plus (t/local-date-time)
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
        :pn (do (println "PROCESS ONLY NEW FILES")
                (c.p/process-b3-folder-only-new))
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
  (c.p/process-b3-folder)
  ;(c.p/process-b3-folder-only-new)

  (clojure.pprint/print-table [:portfolio/ticket :portfolio/quantity :portfolio/average-price] (io.i/get-file-by-entity :portfolio))
  ;;Market data poller

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

  )