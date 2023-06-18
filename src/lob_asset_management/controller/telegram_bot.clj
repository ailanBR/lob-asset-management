(ns lob-asset-management.controller.telegram-bot
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.controller.release :as c.r]
            [lob-asset-management.io.file-in :as io.file-in]
            [lob-asset-management.logic.portfolio :as l.p]
            [lob-asset-management.relevant :refer [telegram-key telegram-personal-chat]]
            [telegrambot-lib.core :as tbot]
            ))

(defn send-message
  ([message]
   (let [mybot (tbot/create telegram-key)]
     (send-message message mybot)))
  ([message mybot]
   (tbot/send-message mybot telegram-personal-chat message {:parse_mode "html"})))

(defn- portfolio-table-message
  [portfolio]
  (str "<b>\uD83D\uDCCA Portfolio allocation \uD83D\uDCCA</b>\n"
       "<pre>"
       "|Ticket|    R$    |  %  |  Res  |\n"
       "|:-----|:--------:|-----|------:|\n"
       (reduce #(str %1
                     "|"
                     (format "%-6s" (-> %2 :portfolio/ticket name))
                     "|"
                     (format "%-10s"
                             (str "R$" (format "%8s" (format "%.2f" (:portfolio/total-last-value %2)))))
                     "|"
                     (format "%5s" (format "%.2f%%" (:portfolio/percentage %2)))
                     "|"
                     (format "%7s" (format "%.1f%%" (:portfolio.profit-loss/percentage %2)))
                     "|\n") "" portfolio)
       "</pre>"))

(defn send-portfolio-table
  ([bot]
   (send-portfolio-table bot (io.file-in/get-file-by-entity :portfolio)))
  ([bot portfolio]
   (let [portfolio-table (portfolio-table-message portfolio)]
     (send-message portfolio-table bot))))

(defn- daily-result-table-message
  [result-release]
  (str "<b>\uD83D\uDCC8 Daily result \uD83D\uDCC9</b>\n"
       "<pre>"
       "|  Ticket  | Last Price |   %   | Last date | Comp date |\n"
       "|----------|:----------:|-------|-----------|-----------|\n"
       (reduce #(str %1
                     "|"
                     (format "%-10s" (-> %2 :ticket name))
                     "|"
                     (format "%-12s"
                             (str "R$" (format "%9s" (format "%.2f" (:last-price %2)))))
                     "|"
                     (format "%7s" (str (format "%.2f" (:diff-percentage %2)) "%"))
                     "|"
                     (format "%11s" (-> %2 :last-price-date name))
                     "|"
                     (format "%11s" (-> %2 :past-date name))
                     "|\n") "" result-release)
       "</pre>"))

(defn send-daily-result
  [bot]
  (let [result-release (c.r/compare-past-price-assets)
        result-message (daily-result-table-message result-release)]
    result-message
    (send-message result-message bot)))

(defn- category-portfolio-message
  [result-release]
  (str "<b>\uD83D\uDCCACategory overview \uD83D\uDCCA</b>\n"
       "<pre>"
       "| Category | Last Price |   %   |\n"                ; Profit R$ |
       "|----------|:----------:|-------|\n"                ;-----------|
       (reduce #(str %1
                     "|"
                     (format "%-10s" (-> %2 :category/name name clojure.string/upper-case))
                     "|"
                     (format "%-12s"
                             (str "R$" (format "%9s" (format "%.2f" (:category/total-last-value %2)))))
                     "|"
                     (format "%7s" (str (format "%.2f" (:category/percentage %2)) "%"))
                     ;"|"
                     ;(format "%-11s"
                     ;        (str "R$" (format "%9s" (format "%.2f" (:category/profit-loss %2)))))
                     "|\n") "" result-release)
       "</pre>"))

(defn send-category-portfolio
  [bot]
  (let [portfolio (io.file-in/get-file-by-entity :portfolio)
        portfolio-category (a.p/get-category-representation portfolio)]
    (send-message (category-portfolio-message portfolio-category) bot)))

(defn- total-currency-row
  [desc
   {:total/keys [current profit-total-percentage]}
   total-current]
  (str "|"
       (format "%-7s" desc)
       "|"
       (format "%-11s" (str "R$" (format "%9s" (format "%.2f" current))))
       "|"
       (format "%7s" (format "%.2f%%" (l.p/position-percentage total-current current)))
       "|"
       (format "%7s" (format "%.1f%%" profit-total-percentage))
       "|\n"))

(defn- total-overview-message
  [{:total/keys [invested current profit-dividend profit-total-percentage profit-total
                 brl usd crypto]}]
  (str "<b>\uD83D\uDCB0 Total overview \uD83D\uDCB0</b>\n"
       "\n"
       "<b>"(format "%-14s" "Current value") ":</b> "
       (str "R$" (format "%9s" (format "%.2f" current))) " "
       (if (pos? profit-total-percentage) " \uD83D\uDCC8 " " \uD83D\uDCC9 ")
       (str (format "%.2f" profit-total-percentage) "%") "\n\n"

       "<b>" (format "%-14s" "Invested") ":</b> "
       (str "R$" (format "%9s" (format "%.2f" invested))) "\n\n"

       "<b>"(format "%-14s" "Profit/loss") ":</b> "
       (str "R$" (format "%9s" (format "%.2f" profit-total))) "\n"

       "<b>" (format "%-14s" "Dividend ") ":</b> "
       (str "R$" (format "%9s" (format "%.2f" profit-dividend))) "\n\n"

       "<pre>"
       "| Moeda |  Amount   |   %   |  Res  |\n"
       "|-------|:---------:|-------|------:|\n"
       (total-currency-row "R$" brl current)
       (total-currency-row "U$D" usd current)
       (total-currency-row "Crypto" crypto current)
       "</pre>"))

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
        total-overview-message
        (send-message bot))))

(def phrases
  ["Se você acha que a instrução é cara, experimente a ignorância - Benjamin Franklin"
   "You are going to be happy said life, but first I will make you strong. - Manuscrito encontrado em Accra, Paulo Coelho"

   "Pois as cores são muitas, mas a luz é uma - liber cordis cincti serpente"
   "Se eu te surpreendi, cê me subestimou - Nova Sorte, Felipe Ret"
   "Os iluminados caminham nas trevas"
   "Não quero ser um rei \nNão quero ser um zé\nSó quero minha moeda \nE a minha de fé\n - That is my way, Edi Rock "])

(defn send-invalid-command
  [bot]
  (send-message (nth phrases (rand-int (count phrases))) bot))

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
   {:hour 21 :minute 00 :f send-daily-result}
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
      "/daily" (send-daily-result bot)
      "/category" (send-category-portfolio bot)
      "/total" (send-total-overview bot)
      "/dividend" (send-invalid-command bot)
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

