(ns lob-asset-management.db.portfolio
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.aux.xtdb :refer [db-node] :as aux.xtdb]
            [lob-asset-management.io.file-in :as io.f-in]
            [lob-asset-management.io.file-out :as io.f-out]
            [lob-asset-management.relevant :refer [config]]
            [xtdb.api :as xt]))

(defmulti get-all (fn [] (or (:env config) :dev)))

(defmethod get-all :dev
  []
  (try
    (io.f-in/get-file-by-entity :portfolio)
    (catch Exception e
      (throw (ex-info "Error when getting portfolio information" {:cause e})))))

(defmethod get-all :prod
  []
  (->> '{:find  [(pull ?p [*])]
         :where [[?p :portfolio/id _]]}
       (aux.xtdb/get! db-node)
       (map #(dissoc % :xt/id))))

;-----------------------------------
(defmulti upsert-bulk! (fn [_] (or (:env config) :dev)))

(defn- already-exist?
  [ticket db-data]
  (if (empty? db-data)
    false
    (let [db-data-tickets (->> db-data (map :portfolio/ticket) set)]
      (contains? db-data-tickets ticket))))

(defn- remove-already-exist-asset
  [assets-keep asset-filtered]
  (remove #(already-exist? (:portfolio/ticket %) assets-keep) asset-filtered))

(defn- maybe-upsert!
  [db-data portfolio]
  (when (not= db-data portfolio)
    (log/info "[UPDATE PORTFOLIO] New portfolio record to be registered")
    (io.f-out/upsert portfolio)
    portfolio))

(defmethod upsert-bulk! :dev
  [portfolio]
  (let [db-data (get-all)]
    (->> []
         (or db-data)
         (remove-already-exist-asset portfolio)
         (concat (or portfolio []))
         (sort-by :portfolio/percentage >)
         (maybe-upsert! db-data))))

(defn id->db-id
  [{:portfolio/keys [ticket] :as portfolio}]
  (let [id (a.p/ticket->portfolio-id ticket)]
    (assoc portfolio :xt/id id
                     :portfolio/id id)))

(defmethod upsert-bulk! :prod
  [portfolio]
  (->> portfolio
       (map id->db-id)
       (aux.xtdb/upsert! db-node))
  (xt/sync db-node))

;-----------------------------------
(defmulti overwrite! (fn [_] (or (:env config) :dev)))

(defmethod overwrite! :dev
  [portfolio]
  (io.f-out/upsert portfolio))

(defmethod overwrite! :prod
  [portfolio]
  (upsert-bulk! portfolio))

;-----------------------------------
(defmulti delete! (fn [_] (or (:env config) :dev)))

(defmethod delete! :dev
  [portfolio]
  (let [all (get-all)
        in-tickets (->> portfolio (map :portfolio/ticket) set)
        updated-all (remove #(contains? in-tickets (:portfolio/ticket %)) all)]
    (if (seq updated-all)
      (io.f-out/upsert updated-all)
      (io.f-in/delete-file :portfolio))))

(defmethod delete! :prod
  [portfolio]
  (doseq [p portfolio]
    (aux.xtdb/delete! db-node (-> p :portfolio/ticket a.p/ticket->portfolio-id))))

(defn delete-all
  []
  (delete! (get-all)))


(defmulti get-by-ticket (fn [_] (or (:env config) :dev)))

(defmethod get-by-ticket :dev
  [ticket]
  (->> (get-all)
       (filter #(= ticket (:portfolio/ticket %)))
       first))

(defmethod get-by-ticket :prod
  [ticket]
  (-> db-node
      xt/db
      (xt/q '{:find  [(pull ?e [*])]
              :in    [?t]
              :where [[?e :portfolio/ticket ?t]]}
            (-> ticket name clojure.string/upper-case keyword))
      ffirst))

(comment
  (get-by-ticket :CNBS)
  )

