(ns lob-asset-management.controller.telegram-bot
  (:require [telegrambot-lib.core :as tbot]
            [lob-asset-management.relevant :refer [telegram-key telegram-personal-chat]]
            [lob-asset-management.io.file-in :as io.file-in]
            ))

(comment

  (def mybot (tbot/create telegram-key))

  (tbot/get-me mybot)

  (tbot/get-updates mybot )
  ;{:offset 63479739} => update_id message or more updated

  (tbot/send-message mybot telegram-personal-chat "Eu sou o Goku")

  (tbot/get-my-commands mybot)

  (def assets (io.file-in/get-file-by-entity :asset))

  (def assets-str (reduce #(str %1 "\n" (-> %2 :asset/ticket name)) "" assets))

  (tbot/send-message mybot telegram-personal-chat assets-str)

  (tbot/send-message mybot telegram-personal-chat "Teste pulando 1 linha \n linha 2")


  )