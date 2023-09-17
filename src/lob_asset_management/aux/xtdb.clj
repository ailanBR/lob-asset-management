(ns lob-asset-management.aux.xtdb
  (:require [clojure.java.io :as io]
            [xtdb.api :as xt]
            [mount.core :as mount :refer [defstate]])
  (:import (clojure.lang PersistentArrayMap PersistentVector))
  (:import (java.util UUID)))

(defn start-xtdb! []
  (letfn [(kv-store [dir]
            {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                        :db-dir (io/file dir)
                        :sync? true}})]
    (xt/start-node
      {:xtdb/tx-log (kv-store "out-data/dev/tx-log")
       :xtdb/document-store (kv-store "out-data/dev/doc-store")
       :xtdb/index-store (kv-store "out-data/dev/index-store")})))

#_(def xtdb-node (start-xtdb!))

(defn stop-xtdb! []
  (.close xtdb-node))

;FIXME : WHY that don't work !!!
#_(defstate db-node
          :start start-xtdb!
          :stop #(.close db-node))

(defn upsert!
  "Insert new record
   require : doc  = array -> e.g [{:test/value 1}] or '({:test/value 1})
             node = xtdb instance -> (start-xtdb!)"
  [node doc]
  (let [tx (mapv (fn [t]
                   (if (:xt/id t)
                     [::xt/put t]
                     [::xt/put (assoc t
                                 :xt/id (UUID/randomUUID))]))
                 doc)]
    (xt/submit-tx node tx)))

(defn get-all!
  [node]
  (->> '{:find  [(pull ?e [*])]
         :where [[?e :xt/id _]]}
      (xt/q (xt/db node))
      (map first)))

(comment
  (def xtdb-node (start-xtdb!))
  (stop-xtdb!)

  ;INSERT
  (xt/submit-tx xtdb-node [[::xt/put {:xt/id "2"
                                      :schedule/name "send top and bottom today prices"}]])

  (upsert! xtdb-node
           [{:xt/id #uuid"776d0415-3bed-466c-9709-ffc952fbd10f"
             :schedule/name "test to delete after 2 changed"}])

  ;GET
  (xt/q (xt/db xtdb-node) '{:find [(pull ?e [*])]
                            :where [[?e :xt/id _]]})

  (xt/submit-tx xtdb-node [[::xt/delete #uuid"0c5a0035-4cab-4c93-8495-430ae2699d49"]])

  (xt/sync xtdb-node)

  (xt/entity-history xtdb-node )

  (type '(1))

  (mapv (fn [t]
          (if (:xt/id t)
            [::xt/put t]
            [::xt/put (assoc t
                        :xt/id (UUID/randomUUID))]))
        '({:a 1}))

  (get! xtdb-node :xt/id "1")

  (xt/pull (xt/db xtdb-node) [:xt/id :schedule/name] "1")
  (def g (get-all! xtdb-node))
  )
