(ns lob-asset-management.db.schedule
  (:require [schema.core :as s]
            [lob-asset-management.aux.xtdb :refer [db-node] :as aux.xtdb]
            [lob-asset-management.aux.time :as aux.t]))

;; USE CRON
;; https://github.com/sbocq/cronit
;; http://www.nncron.ru/help/EN/working/cron-format.htm
;
;(s/defschema Schedule
;  #:schedule{:name s/Str
;             :created-at s/Inst
;             :schedule-at s/Inst
;             :last-execution-at s/Inst})
;
;(s/defn upsert!
;  [schedule :- Schedule]
;  (aux.xtdb/upsert! db-node schedule))
;
;(defn get-all!
;  []
;  (aux.xtdb/get! db-node '{:find  [(pull ?e [*])]
;                           :where [[?e :schedule/name _]]}))
;
;(comment
;
;  (type (aux.t/get-current-millis))
;  (type (aux.t/current-date-time))
;
;  (s/validate Schedule
;              #:schedule{:name "A"
;                         :schedule-at #inst"2023-09-18T16:24:27.698-00:00"
;                         :last-execution-at #inst"2023-09-18T16:24:27.698-00:00"})
;
;  (clojure.instant/read-instant-date (aux.t/current-datetime->str))
;  )
