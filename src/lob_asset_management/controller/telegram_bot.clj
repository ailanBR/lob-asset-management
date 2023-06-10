(ns lob-asset-management.controller.telegram-bot
  (:require [telegrambot-lib.core :as tbot]
            [lob-asset-management.relevant :refer [telegram-key telegram-personal-chat]]
            [lob-asset-management.io.file-in :as io.file-in]
            ))

(comment

  (def mybot (tbot/create telegram-key))

  (tbot/get-me mybot)

  (tbot/get-updates mybot)
  ;{:offset 63479739} => update_id message or more updated

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


  )
