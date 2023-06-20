(ns lob-asset-management.controller.telegram-bot
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.adapter.telegram :as a.t]
            [lob-asset-management.controller.release :as c.r]
            [lob-asset-management.io.file-in :as io.file-in]
            [lob-asset-management.relevant :refer [telegram-key telegram-personal-chat]]
            [telegrambot-lib.core :as tbot]))

(defn send-message
  ([message]
   (let [mybot (tbot/create telegram-key)]
     (send-message message mybot)))
  ([message mybot]
   (tbot/send-message mybot telegram-personal-chat message {:parse_mode "html"})))

(defn send-portfolio-table
  ([bot]
   (send-portfolio-table bot (io.file-in/get-file-by-entity :portfolio)))
  ([bot portfolio]
   (let [portfolio-table (a.t/portfolio-table-message portfolio)]
     (send-message portfolio-table bot))))

(defn send-asset-daily-price-change                         ;TODO: Consider portfolio average price
  [bot]
  (let [result-release (c.r/compare-past-day-price-assets 1)
        result-message (a.t/asset-daily-change-message result-release)]
    (send-message result-message bot)))

(defn send-asset-price-change                               ;TODO: Consider portfolio average price
  [bot]
  (let [assets (io.file-in/get-file-by-entity :asset)
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
    (send-message result-message bot)))

(defn send-category-portfolio
  [bot]
  (let [portfolio (io.file-in/get-file-by-entity :portfolio)
        portfolio-category (a.p/get-category-representation portfolio)]
    (send-message (a.t/category-portfolio-message portfolio-category) bot)))

(defn send-total-overview
  [bot]
  (let [portfolio (io.file-in/get-file-by-entity :portfolio)
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
        (assoc :total/brl brl-total :total/usd usd-total :total/crypto crypto-total)
        a.t/total-overview-message
        (send-message bot))))

(defn send-assets-message
  [bot]
  (let [portfolio (io.file-in/get-file-by-entity :portfolio)]
    (send-message (a.t/assets-table-message portfolio) bot)))

(defn send-invalid-command
  [bot]
  (send-message (nth a.t/phrases (rand-int (count a.t/phrases))) bot))

(def config {:timeout 10}) ;the bot api timeout is in seconds

(defn poll-updates
  "Long poll for recent chat messages from Telegram."
  ([bot]
   (poll-updates bot nil))

  ([bot offset]
   (let [resp (tbot/get-updates bot {:offset offset
                                     :timeout (:timeout config)})]
     (if (contains? resp :error)
       (log/error "tbot/get-updates error:" (:error resp))
       resp))))

(def auto-message-scheduled
  [{:hour 21 :minute 00 :f send-total-overview}
   {:hour 21 :minute 00 :f send-asset-daily-price-change}
   {:hour 17 :minute 38 :f send-portfolio-table}])

(defn auto-message
  [bot time interval]
  (map (fn [{:keys [hour minute f]}]
         (when (and (= (.getHour time) hour)
                    (= (.getMinute time) minute)
                    (< (.getSecond time) interval))   ;FIXME use atom ?
           (f bot))) auto-message-scheduled))

(defn mybot
  []
  (tbot/create telegram-key))

(defn handle-msg
  [bot msg]
  (let [msg-txt (-> msg :message :text)]
    (condp = msg-txt
      "/portfolio" (send-portfolio-table bot)
      "/daily" (send-asset-daily-price-change bot)
      "/category" (send-category-portfolio bot)
      "/total" (send-total-overview bot)
      "/dividend" (send-invalid-command bot)
      "/assets" (send-assets-message bot)
      "/prices" (send-asset-price-change bot)
      (send-invalid-command bot))))

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
  )

