(ns lob-asset-management.controller.telegram-bot
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.adapter.telegram :as a.t]
            [lob-asset-management.controller.portfolio :as c.p]
            [lob-asset-management.controller.release :as c.r]
            [lob-asset-management.controller.metric :as c.m]
            [lob-asset-management.db.asset :as db.a]
            [lob-asset-management.db.portfolio :as db.p]
            [lob-asset-management.db.telegram :as db.t]
            [lob-asset-management.relevant :refer [telegram-key telegram-personal-chat]]
            [mount.core :refer [defstate]]
            [telegrambot-lib.core :as tbot]))

;TODO: Get saved messages

(defstate bot
          :start (tbot/create telegram-key))

(defn send-message
  ([message]
   (let [mybot (tbot/create telegram-key)]
     (send-message message mybot)))
  ([message mybot]
   (tbot/send-message mybot telegram-personal-chat message {:parse_mode "html"})))

(defmulti send-command (fn [_ _ cmd] cmd))

(def commands
  {:portfolio {:fn   #(send-command %1 %2 :portfolio)
               :desc "Portfolio Table"}
   :daily     {:fn   #(send-command %1 %2 :daily)
               :desc "D-1 price changes"}
   :category  {:fn   #(send-command %1 %2 :category)
               :desc "Allocation by category"}
   :total     {:fn   #(send-command %1 %2 :total)
               :desc "Total invested informations"}
   ;:dividend  {:fn   #(send-command % :dividend)
   ;            :desc "Total dividend received"}
   :assets    {:fn   #(send-command %1 %2 :assets)
               :desc "Assets in portfolio"}
   :prices    {:fn   #(send-command %1 %2 :prices)
               :desc "Total price changes"}
   :alpha-api {:fn   #(send-command %1 %2 :alpha-api)
               :desc "Metrics about alpha api calls"}
   :commands  {:fn   #(send-command %1 %2 :commands)
               :desc "List of allowed commands"}
   :s         {:fn   #(send-command %1 %2 :save)
               :desc "Save anything"}})

(defmethod send-command :portfolio
  [mybot _ _]
  (let [portfolio-table (a.t/portfolio-table-message (db.p/get-all))]
    (send-message portfolio-table mybot)))

(defmethod send-command :daily
  [mybot _ _]
  (let [result-release (c.r/compare-past-day-price-assets 1)
        result-message (a.t/asset-daily-change-message result-release)]
    (send-message result-message mybot)))

(defmethod send-command :prices
  [mybot _ _]
  (let [assets (db.a/get-all)
        day (c.r/compare-past-day-price-assets assets 1)
        day' (map (fn [{:keys [ticket last-price diff-percentage]}]
                    {:ticket              ticket
                     :last-price          last-price
                     :day/diff-percentage diff-percentage})
                  day)
        day+weekly (map (fn [{:keys [diff-percentage] weekly-ticket :ticket}]
                          (-> #(= (:ticket %) weekly-ticket)
                              (filter day')
                              first
                              (assoc :weekly/diff-percentage diff-percentage)))
                       (c.r/compare-past-day-price-assets assets 7))
         result (map (fn [{:keys [diff-percentage] month-ticket :ticket}]
                       (-> #(= (:ticket %) month-ticket)
                           (filter day+weekly)
                           first
                           (assoc :month/diff-percentage diff-percentage)))
                     (c.r/compare-past-day-price-assets assets 30))
        result-message (a.t/asset-price-change-message result)]
    (send-message result-message mybot)))

(defmethod send-command :category
  [mybot _ _]
  (let [portfolio (db.p/get-all)
        portfolio-category (c.p/get-category-representation portfolio)]
    (send-message (a.t/category-portfolio-message portfolio-category) mybot)))

(defmethod send-command :total
  [mybot _ _]
  (let [portfolio (db.p/get-all)
        portfolio-total (a.p/get-total portfolio)
        brl-total (->> portfolio
                       (filter #(or (contains? (:portfolio/exchanges %) :nu)
                                    (contains? (:portfolio/exchanges %) :inter)))
                       a.p/get-total)
        usd-total (->> portfolio
                       (filter #(contains? (:portfolio/exchanges %) :sproutfy ))
                       a.p/get-total)
        crypto-total (->> portfolio
                          (filter #(or (contains? (:portfolio/exchanges %) :freebtc)
                                       (contains? (:portfolio/exchanges %) :binance)
                                       (contains? (:portfolio/exchanges %) :localbitoin)
                                       (contains? (:portfolio/exchanges %) :pancakeswap)))
                          a.p/get-total)]
    (-> portfolio-total
        (merge {:total/brl brl-total :total/usd usd-total :total/crypto crypto-total})
        a.t/total-overview-message
        (send-message mybot))))

(defmethod send-command :assets
  [mybot _ _]
  (let [portfolio (db.p/get-all)]
    (send-message (a.t/assets-table-message portfolio) mybot)))

(defmethod send-command :alpha-api
  [mybot _ _]
  (let [calls (c.m/total-api-calls)]
    (send-message (a.t/alpha-api-calls-message calls) mybot)))

(defmethod send-command :commands
  [mybot _ _]
  (send-message (a.t/commands-message commands) mybot))

(defmethod send-command :save
  [mybot msg _]
  (db.t/insert! msg)
  (send-message "Message received \uD83E\uDDDE" mybot))

(defn send-invalid-command
  [mybot]
  (send-message (nth a.t/phrases (rand-int (count a.t/phrases))) mybot))

(defn send-error-command
  [mybot exception]
  (send-message (str "Houston, we have a problem \uD83D\uDCA9 \n\n" (ex-message exception)) mybot))

(def config {:timeout 10}) ;the bot api timeout is in seconds

(defn pull-updates
  "Long poll for recent chat messages from Telegram."
  ([mybot]
   (pull-updates mybot nil))

  ([mybot offset]
   (let [resp (tbot/get-updates mybot {:offset offset
                                       :timeout (:timeout config)})]
     (if (contains? resp :error)
       (log/error "tbot/get-updates error:" (:error resp))
       resp))))

(def auto-message-scheduled
  [{:hour 21 :minute 00 :f #(send-command % :total)}
   {:hour 21 :minute 00 :f #(send-command % :daily)}
   {:hour 17 :minute 38 :f #(send-command % :portfolio)}])

(defn auto-message
  [mybot time interval]
  (map (fn [{:keys [hour minute f]}]
         (when (and (= (.getHour time) hour)
                    (= (.getMinute time) minute)
                    (< (.getSecond time) interval))   ;FIXME use atom ?
           (f mybot))) auto-message-scheduled))

(defn mybot
  []
  (tbot/create telegram-key))

(defn handle-msg
  [mybot msg]
  (try
    (when (= 772662600 (-> msg :message :from :id))
      (let [msg-txt (-> msg :message :text)
            {:telegram/keys [command] :as message} (a.t/msg-out->msg-in msg-txt)]
        (log/info "[Telegram BOT] Message received " msg-txt)
        (if-let [command-fn (-> commands command :fn)]
          (command-fn mybot message)
          (send-invalid-command mybot))))
    (catch Exception e
      (send-error-command mybot e))))

(comment
  (def mybot (tbot/create telegram-key))

  (tbot/get-me mybot)

  (tbot/get-updates mybot {:offset 63479744})

  (def update (tbot/get-updates mybot 63479743))
  ;{:offset 63479739} => update_id message or more updated
  (-> update :result last :message :text)

  (tbot/send-message mybot telegram-personal-chat "Eu sou o Goku")

  (tbot/get-my-commands mybot)

  (tbot/send-message mybot telegram-personal-chat "Teste pulando 1 linha \n linha 2" {:parse_mode "MarkdownV2"})

  (tbot/send-document )

  (tbot/set-my-commands )

  )

