(ns lob-asset-management.controller.telegram-bot
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.adapter.portfolio :as a.p]
            [lob-asset-management.controller.release :as c.r]
            [lob-asset-management.io.file-in :as io.file-in]
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
       "<pre>\n"
       "| Ticket   |      R$     |   %   |\n"
       "|----------|:-----------:|------:|\n"
       (reduce #(str %1
                     "|"
                     (format "%-10s" (-> %2 :portfolio/ticket name))
                     "|"
                     (format "%-13s"
                             (str "R$" (format "%9s" (format "%.2f" (:portfolio/total-last-value %2)))))
                     "|"
                     (format "%-7s" (str (format "%.2f" (:portfolio/percentage %2)) "%"))
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
       "<pre>\n"
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
       "<pre>\n"
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

(defn- total-overview-message
  [{:total/keys [invested current profit-dividend profit-total-percentage profit-total
                 brl-value brl-percentage usd-value usd-percentage crypto-value crypto-percentage]}]
  (str "<b>\uD83D\uDCB0 Total overview \uD83D\uDCB0</b>\n"
       "\n"
       "<b>"(format "%-14s" "Current value") ":</b> "
       (str "R$" (format "%9s" (format "%.2f" current))) " "
       (if (pos? profit-total-percentage) " \uD83D\uDCC8 " " \uD83D\uDCC9 ")
       (str (format "%.2f" profit-total-percentage) "%") "\n\n"
       "<pre>\n"
       "| Moeda |  Total R$  |    %   |\n"
       "|-------|:----------:|-------:|\n"
       "|"
       (format "%-7s" "R$")
       "|"
       (format "%-12s" (str "R$" (format "%10s" (format "%.2f" brl-value))))
       "|"
       (format "%8s" (format "%.2f %%" brl-percentage))
       "|\n"
       "|"
       (format "%-7s" "U$D")
       "|"
       (format "%-12s" (str "R$" (format "%10s" (format "%.2f" usd-value))))
       "|"
       (format "%8s" (format "%.2f %%" usd-percentage))
       "|\n"
       "|"
       (format "%-7s" "Crypto")
       "|"
       (format "%-12s" (str "R$" (format "%10s" (format "%.2f" crypto-value))))
       "|"
       (format "%8s" (format "%.2f %%" crypto-percentage))
       "|\n"

       "</pre>"

       "\n\n"

       "<b>" (format "%-14s" "Invested") ":</b> "
       (str "R$" (format "%9s" (format "%.2f" invested))) "\n\n"


       "<b>"(format "%-14s" "Profit/loss") ":</b> "
       (str "R$" (format "%9s" (format "%.2f" profit-total))) "\n"


       "<b>" (format "%-14s" "Dividend ") ":</b> "
       (str "R$" (format "%9s" (format "%.2f" profit-dividend))) "\n"))

(defn send-total-overview
  [bot]
  (let [portfolio (io.file-in/get-file-by-entity :portfolio)
        portfolio-total (a.p/get-total portfolio)]
    (send-message (total-overview-message portfolio-total) bot)))

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
                    (< (.getSecond time) (* 2 interval)))   ;FIXME use atom ?
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

  (def assets (io.file-in/get-file-by-entity :asset))
  (def portfolio (io.file-in/get-file-by-entity :portfolio))

  (def assets-str (reduce #(str %1 "\n" (-> %2 :asset/ticket name)) "" assets))
  (tbot/send-message mybot telegram-personal-chat assets-str)

  (def portfolio-resume
    (reduce #(str %1
                  "\n"
                  (-> "%-10s"
                      (format (-> %2 :portfolio/ticket name))
                      (clojure.string/replace #" " "-")
                      )
                  "R$ "
                  (format "%.2f" (:portfolio/total-last-value %2))
                  " "
                  "(" (format "%.2f" (:portfolio/percentage %2)) "%)"
                  )
            "*Portfolio allocation*"
            portfolio))
  (tbot/send-message mybot telegram-personal-chat portfolio-resume)

  (def portfolio-resume-html
    (reduce #(str %1
                  "\n"
                  (-> "%-10s"
                      (format (-> %2 :portfolio/ticket name))
                      (clojure.string/replace #" " ".")
                      )
                  "R$ "
                  (format "%.2f" (:portfolio/total-last-value %2))
                  " "
                  "(" (format "%.2f" (:portfolio/percentage %2)) "%)"
                  )
            "<b>\uD83D\uDCCA Portfolio allocation \uD83D\uDCCA</b>\n"
            portfolio))
  ;Emoji list https://www.w3schools.com/charsets/ref_emoji.asp
  (tbot/send-message mybot telegram-personal-chat portfolio-resume-html {:parse_mode "html"})

  (def portfolio-table
    (str "<b>\uD83D\uDCCA Portfolio allocation \uD83D\uDCCA</b>\n"
         "<pre>\n"
         "| Ticket   |      R$     |   %   |\n"
         "|----------|:-----------:|------:|\n"
         (reduce #(str %1
                       "|"
                       (format "%-10s" (-> %2 :portfolio/ticket name))
                       "|"
                       (format "%-13s"
                               (str "R$" (format "%9s" (format "%.2f" (:portfolio/total-last-value %2)))))
                       "|"
                       (format "%-7s" (str (format "%.2f" (:portfolio/percentage %2)) "%"))
                       "|\n") "" portfolio)
         "</pre>"))

  (tbot/send-message mybot telegram-personal-chat portfolio-table {:parse_mode "html"})

  (tbot/send-message mybot telegram-personal-chat "Teste pulando 1 linha \n linha 2" {:parse_mode "MarkdownV2"})

  (send-daily-result)

  )

