(ns lob-asset-management.core
  (:require [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.aux.xtdb :refer [db-node]]
            [lob-asset-management.cli :as cli]
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
            [lob-asset-management.io.google_api :as io.storage]
            [lob-asset-management.relevant :refer [config config-prod]]
            [mount.core :as mount]))

(defn start [env]
  "Default env = :dev

  v2 => mount functions to be used by environment"
  (mount/start #'lob-asset-management.relevant/config
               #'lob-asset-management.relevant/alpha-key
               #'lob-asset-management.relevant/telegram-key
               #'lob-asset-management.relevant/telegram-personal-chat
               #'lob-asset-management.relevant/spread-sheet-config
               ;#'lob-asset-management.relevant/google-oauth
               #'lob-asset-management.controller.telegram-bot/bot
               #'lob-asset-management.io.google_api/spread-sheet-service
               )

  (when (= :prod env)
    (schema.core/set-fn-validation! true)
    (mount/start #'lob-asset-management.aux.xtdb/db-node)
    (mount/start-with {#'lob-asset-management.relevant/config config-prod})))

(defn stop []
  (mount/stop #'lob-asset-management.relevant/config
              #'lob-asset-management.relevant/alpha-key
              #'lob-asset-management.relevant/telegram-key
              #'lob-asset-management.relevant/telegram-personal-chat
              #'lob-asset-management.controller.telegram-bot/bot
              #'lob-asset-management.relevant/spread-sheet-config
              #'lob-asset-management.aux.xtdb/db-node))

;(start :dev)                                                     ;for develop purpose
;(stop)

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn snapshot []
  (db.a/snapshot)
  (db.p/snapshot)
  (db.t/snapshot)
  (db.f/snapshot))

(defn -main [& args]
  (start :prod)
  (let [{:keys [action options exit-message ok?]} (cli/validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "start" (let [interval 3000
                      stop-loop (c.s/poller interval)
                      #_(poller "Main"
                              #(start-processing #{11 12 13 14 15 16 17 18 19 20 21 22 23} interval)
                              13000
                              #{7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 00 01})]
                  (snapshot)
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
  (lob-asset-management.db.transaction/get-by-id #uuid "ecc07451-4280-3f44-b278-ca6c8df07848")

  ;=========================================

  (clojure.pprint/print-table
    [:portfolio/ticket :portfolio/quantity]
    (->> (db.p/get-all)
         #_(filter #(or (= :SQIA3 (:portfolio/ticket %))
                        (= :EVTC31 (:portfolio/ticket %))
                        (= :E9TC11 (:portfolio/ticket %))))
         (filter #(contains? #{:nu :inter} (first (:portfolio/exchanges %))))
         ;(filter #(contains? #{:sproutfy} (first (:portfolio/exchanges %))))
         ;(filter #(= :crypto (:portfolio/category %)))
         ;(filter #(or (contains? (:portfolio/exchanges %) :nu)
         ;             (contains? (:portfolio/exchanges %) :inter)))
         ;;(remove #(= 0.0 (:portfolio/quantity %)))
         (sort-by :portfolio/ticket)))

  (letfn [(maybe-update-price
            [{:asset/keys              [type]
              :asset.market-price/keys [price]}
             {usd-price :forex-usd/price}]
            (if (= type :stockEUA) (* price usd-price) price))
          (get-asset [assets forex ticket]
            (-> (filter (fn [{asset-ticket :asset/ticket}] (= asset-ticket ticket)) assets)
                first
                (maybe-update-price forex)))]
    (let [assets (db.a/get-all)
          forex (db.f/get-all)]
      (->> (db.p/get-all)
           (map #(dissoc % :portfolio/transaction-ids))
           (remove #(= 0.0 (:portfolio/quantity %)))
           #_(filter #(or (= :ABEV3 (:portfolio/ticket %))
                          (= :GOOGL (:portfolio/ticket %))))
           (map (fn [{:portfolio/keys [ticket] :as portfolio}]
                  (let [last-price (get-asset assets forex ticket)]
                    (assoc portfolio :last-price last-price))))
           (sort-by :portfolio.profit-loss/percentage)
           (clojure.pprint/print-table [:portfolio/ticket
                                        :portfolio/quantity
                                        :portfolio/average-price
                                        :last-price
                                        :portfolio.profit-loss/percentage]))))

  (filter #(= :SMTO3 (:portfolio/ticket %)) (db.p/get-all))

  (clojure.pprint/print-table
    [:transaction/id :transaction.asset/ticket :transaction/created-at :transaction/operation-type :transaction/quantity :transaction/average-price :transaction/operation-total]
    (->> (lob-asset-management.db.transaction/get-all)
         ;(filter #(= :fraçãoemativos (:transaction/operation-type %)))
         ;;(filter #(clojure.string/includes? (name (:transaction.asset/ticket %)) "ALZR"))
         ;;(filter #(clojure.string/includes? (name (:transaction.asset/ticket %)) "EQTL"))
         ;;(filter #(clojure.string/includes? (name (:transaction.asset/ticket %)) "FLRY"))
         (filter #(clojure.string/includes? (name (:transaction.asset/ticket %)) "OIBR"))
         ;;(filter #(or (= :ALZR13 (:transaction.asset/ticket %))))
         ;(remove #(contains?
         ;           #{:sell :JCP :income :dividend :bonus
         ;             :fraçãoemativos :transferência :waste :incorporation}
         ;           (:transaction/operation-type %)))
         #_(filter #(or (= :ALZR12 (:transaction.asset/ticket %))
                      (= :ALZR13 (:transaction.asset/ticket %))
                      (= :ALZR11 (:transaction.asset/ticket %))))
         ;(filter #(contains? #{:sproutfy} (:transaction/exchange %)))
         ;(sort-by :transaction/exchange)
         ;(sort-by :transaction.asset/ticket)
         (sort-by :transaction/created-at)
         ))

  (->> (lob-asset-management.db.transaction/get-all)
       ;(filter #(= :fraçãoemativos (:transaction/operation-type %)))
       ;(filter #(or (= :SULA11 (:transaction.asset/ticket %))))
       ;(remove #(contains?
       ;           #{:sell :JCP :income :dividend :bonus
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
  (c.p/update-portfolio-representation (db.p/get-all) (db.f/get-all))

  (clojure.pprint/print-table
    [:asset/ticket :asset.market-price/price-date ]
    (->> (db.a/get-all)
         (filter #(or (= (:asset/type %) :stockBR)
                    (= (:asset/type %) :stockEUA)))
         (sort-by :asset.market-price/price-date)
         #_(take 30)))


  )
