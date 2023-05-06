(ns lob-asset-management.core
  (:require [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.adapter.transaction :as a.t]
            [lob-asset-management.io.file-out :as io.o]
            [lob-asset-management.io.file-in :as io.i]
            [lob-asset-management.controller.process-file :as c.p]
            [lob-asset-management.controller.market :as c.m]
            [java-time.api :as t]))

;FIXME : Include log lib https://mattjquinn.com/2014/log4j2-clojure/ to avoid error

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defn -main
  ;FIXME : Command don't executed
  ; NOP :- 1. Maybe is the root path, the function don't find the files
  ; 2. Don't have access to read/write in command line
  [& args]
  (when (nil? args)
    (println "SELECT A COMMAND")
    (println " p -> Process all files in the release folder")
    (println " d -> Delete all files in the release folder")
    (println " pn -> Process new files in the release folder"))
  (when-let [read (or (first args) (read-line))]
    (let [command (-> read clojure.string/lower-case keyword)]
      (condp = command
        :p (do (println "PROCESSING FILE FOLDERS")
               (c.p/process-b3-folder))
        :d (do (println "DELETING ALL FILES IN FOLDER")
               (c.p/delete-all-files))
        :pn (do (println "PROCESS ONLY NEW FILES")
                (c.p/process-b3-folder-only-new))
        (println "INVALID COMMAND"))))
  (println "FINISH"))

(defn pooler [f interval]
  (let [run-forest-run (atom true)]
    (future
      (try
        (while @run-forest-run
          (f)
          (Thread/sleep interval))
        (catch Exception e
          (println "Error in pooler:" e))))
    (fn [] (reset! run-forest-run false))))

(comment
  "Read b3 movements and write a new edn file with assets without duplicated"
  "1. Read B3 movements"
  (def b3-mov (io.i/read-xlsx-by-file-name "movimentacao-20220101-20220630.xlsx"))
  "2. Transform in atoms"
  (def assets (map a.a/movement->asset b3-mov))
  "3. Remove duplicated [LONG WAY]"
    ;"3.1 Create a temp atom (Kind of 'variable') for assets"
    ;(def atom-assets (atom #{}))
    ;"3.3 Add uniques asset in the temp atom"
    ;(defn filter-assets
    ;  [{:asset/keys [ticket] :as a}]
    ;  (let [asset (->> @atom-assets (filter #(= ticket (:asset/ticket %))))]
    ;    (when (empty? asset)
    ;      (swap! atom-assets conj a))))
    ;
    ;(def filtered-atoms (map filter-assets assets))
    ;
    ;"3.4 Remove nil from the list"
    ;(def final-assets (filter #(not (nil? %)) filtered-atoms))
  "3. Remove duplicated "
  (defn movements->assets
    [mov]
    (->> mov
         (map a.a/movement->asset)
         (group-by :asset/ticket)
         (map #(-> % val first))))

  (io.o/upsert assets)

  (def b3-mov (lob-asset-management.io.file-in/read-xlsx-by-file-name "movimentacao-20220101-20220630.xlsx"))
  (map a.a/movements->assets b3-mov)

  (def assets (map a.a/movement->asset b3-mov))
  (filter #(= [:unknown] (:asset/category %)) assets)

  (-> (filter #(= (:product (first b3-mov))
                  (:asset/name %)) assets) first)

  (defn get-asset-by-name
    [assets name]
    (-> (filter #(= name (:asset/name %)) assets) first))

  (defn b3-movements->transactions
    [mov]
    (let [asset (map a.a/movement->asset b3-mov)]
      (map #(a.t/movements->transaction % (get-asset-by-name asset (:product %))) b3-mov)))

  (b3-movements->transactions b3-mov)

  (c.p/process-b3-movement b3-mov)

  (c.p/process-b3-release "movimentacao-20220101-20220630.xlsx")

  (schema.core/set-fn-validation! true)

  (c.p/delete-all-files)
  (c.p/process-b3-folder)
  (c.p/process-b3-folder-only-new)

  (clojure.pprint/print-table [:portfolio/ticket :portfolio/quantity :portfolio/total-cost :portfolio/dividend ] (io.i/get-file-by-entity :portfolio))

  ;;Market Controller
  (def assets (io.i/get-file-by-entity :asset))
  (def updated-assets
    (->> assets
         (sort-by :asset.market-price/updated-at)
         (#(a.a/get-less-market-price-updated % 2))
         (map c.m/get-asset-market-price)))

  (->> updated-assets
       (sort-by :asset.market-price/updated-at)
       (#(a.a/get-less-market-price-updated % 1))
       (map c.m/get-asset-market-price))

  ;;Market data pooler

  (defn my-function []
    (println "Hello, world! [" (str (t/local-date-time)) "]"))

  (def get-market-price-pooler (pooler #(c.m/update-asset-market-price) 15000))

  (get-market-price-pooler)

  (let [stop-loop (pooler my-function 15000)]
    (Thread/sleep 60000)
    (stop-loop))


  )