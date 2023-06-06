(ns lob-asset-management.controller.telegram-bot
  (:require [telegrambot-lib.core :as tbot]
            [lob-asset-management.relevant :refer [telegram-key telegram-personal-chat]]))

(comment


  (def mybot (tbot/create telegram-key))

  (tbot/get-me mybot)

  (tbot/get-updates mybot)

  (tbot/send-message mybot telegram-personal-chat "Eu sou o Goku")


  (tbot/get-my-commands mybot)




  )