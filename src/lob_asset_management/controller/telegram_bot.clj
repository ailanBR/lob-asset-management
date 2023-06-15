(ns lob-asset-management.controller.telegram-bot
  (:require [clojure.tools.logging :as log]
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

(defn portfolio-table-message
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
  ([]
   (send-portfolio-table (io.file-in/get-file-by-entity :portfolio)))
  ([portfolio]
   (let [portfolio-table (portfolio-table-message portfolio)]
     (send-message portfolio-table))))

(defn daily-result-table-message
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
  []
  (let [result-release (c.r/compare-past-price-assets)
        result-message (daily-result-table-message result-release)]
    (send-message result-message)))

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

(def config
  {:timeout 10}) ;the bot api timeout is in seconds

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

(defn mybot
  []
  (tbot/create telegram-key))

(defn handle-msg
  [bot msg]
  (let [msg-txt (-> msg :message :text)]
    (condp = msg-txt
      "/portfolio" (send-portfolio-table)
      "/daily" (send-daily-result)
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

