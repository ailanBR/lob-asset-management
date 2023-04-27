(ns lob-asset-management.core
  (:require [lob-asset-management.adapter.asset :as a.a]
            [lob-asset-management.adapter.transaction :as a.t]
            [lob-asset-management.io.file-out :as io.o]
            [lob-asset-management.io.file-in :as io.i]
            [lob-asset-management.controller.process-file :as c.p]))

;FIXME : Include log lib https://mattjquinn.com/2014/log4j2-clojure/ to avoid error

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

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

  ;


  (c.p/delete-all-files)
  (c.p/process-b3-folder)


  (clojure.pprint/print-table [:portfolio/ticket :portfolio/quantity] (io.i/get-file-by-entity :portfolio))


  )