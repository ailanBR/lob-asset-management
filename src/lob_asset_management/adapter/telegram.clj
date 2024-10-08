(ns lob-asset-management.adapter.telegram
  (:require [clojure.string :as str]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.aux.util :refer [string->uuid]]
            [lob-asset-management.aux.money :refer [safe-number->bigdec]]
            [lob-asset-management.logic.portfolio :as l.p]
            [lob-asset-management.aux.time :as t]
            [lob-asset-management.models.telegram :as m.t]
            [schema.core :as s])
  (:import (java.util UUID)))

(defn portfolio-table-message
  [portfolio]
  (str "<b>\uD83D\uDCCA Portfolio allocation \uD83D\uDCCA</b>\n"
       "<pre>"
       "|Ticket|    R$    |  %   |  Res  |\n"
       "|:-----|:--------:|------|------:|\n"
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

(defn asset-daily-change-message
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

(defn asset-price-change-message
  [result-release]
  (str "<b>\uD83D\uDCC8 Daily result \uD83D\uDCC9</b>\n"
       "<pre>"
       "|Ticket|  Last R$  | D-1%  |Weekly%|Month% |\n"
       "|------|:---------:|-------|-------|-------|\n"
       (reduce #(str %1
                     "|"
                     (format "%-6s" (-> %2 :ticket name))
                     "|"
                     (format "%-11s"
                             (str "R$" (format "%8s" (format "%.2f" (:last-price %2)))))
                     "|"
                     (format "%7s" (str (format "%.2f" (:day/diff-percentage %2)) "%"))
                     "|"
                     (format "%7s" (str (format "%.2f" (:weekly/diff-percentage %2)) "%"))
                     "|"
                     (format "%7s" (str (format "%.2f" (:month/diff-percentage %2)) "%"))
                     "|\n") "" result-release)
       "</pre>"))

(defn category-portfolio-message
  [result-release]
  (str "<b>\uD83D\uDCCACategory overview \uD83D\uDCCA</b>\n"
       "<pre>"
       "| Category | Last Price |   %   |\n"                ; Profit R$ |
       "|----------|:----------:|-------|\n"                ;-----------|
       (reduce #(str %1
                     "|"
                     (format "%-10s" (-> %2 :category/name name str/upper-case))
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

(defn total-currency-row
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

(defn total-overview-message
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

(defn get-value-fraction
  [value]
  (let [splited (-> value str (clojure.string/split #"\."))]
    (if (> (count splited) 1)
      (->> splited last (str "0.") safe-number->bigdec)
      0M)))

(defn assets-table-message
  [portfolio]
  (let [number (atom 0)]
    (str "<b>\uD83D\uDCCA Portfolio allocation \uD83D\uDCCA</b>\n"
         "<pre>"
         "|n.|Ticket| Category |  %  | Qnt  |\n"
         "|--|:-----|:--------:|-----|------|\n"
         (reduce (fn [current {:portfolio/keys [ticket category percentage quantity]}]
                   (swap! number inc)
                   (str current
                        "|"
                        (format "%2s" @number)
                        "|"
                        (format "%-6s" (name ticket))
                        "|"
                        (format "%-10s" (name category))
                        "|"
                        (format "%5s" (format "%.2f%%" percentage))
                        "|"
                        (format "%5s" (if (> (get-value-fraction quantity) 0)
                                        (format "%.3f" quantity)
                                        (format "%.1f" quantity)))
                        "|\n")) "" portfolio)
         "</pre>")))

(def phrases
  ["Se você acha que a instrução é cara, experimente a ignorância - Benjamin Franklin"
   "You are going to be happy said life, but first I will make you strong. - Manuscrito encontrado em Accra, Paulo Coelho"

   "Pois as cores são muitas, mas a luz é uma - liber cordis cincti serpente"
   "Se eu te surpreendi, cê me subestimou - Nova Sorte, Felipe Ret"
   "Os iluminados caminham nas trevas"
   "Não quero ser um rei \nNão quero ser um zé\nSó quero minha moeda \nE a minha de fé\n - That is my way, Edi Rock "])

(defn alpha-api-calls-message [{:keys [today total daily-limit]}]
  (str "<b>\uD83C\uDF10 Total Alpha API Calls \uD83C\uDF10</b>\n"
       "Total calls today => " today "\n"
       "Limit of " daily-limit " daily calls\n\n"
       "Total calls ever => " total))

(defn commands-message
  [commands]
  (reduce #(str %1
                (format "%-10s" (->> %2 first name (str "/")))
                " - "
                (-> %2 second :desc)
                "\n")
          "" commands))

(s/defn msg->category :- s/Keyword [msg] :uncategorized) ;TODO

(s/defn message->id
  ([{:telegram/keys [command msg created-at category]}]
   (message->id command msg created-at category))
  ([command msg created-at category]
   (string->uuid (str command msg created-at category))))

(s/defn msg-out->msg-in :- m.t/TelegramMessage
  [msg]
  (let [command (-> msg (str/split #" ") first (str/replace "/" "") keyword)
        msg' (-> msg (str/split #" ") rest (->> (str/join " ")))
        created-at (str (t/current-date-time))
        category (msg->category msg)]
    {:telegram/id         (message->id command msg created-at category)
     :telegram/message    msg'
     :telegram/created-at created-at
     :telegram/category   category
     :telegram/active     true
     :telegram/command    command}))

(defn saved-messages
  [db-messages]
  (reduce (fn [current {:telegram/keys [created-at message active]}]
            (if active
              (str current
                   (format "%10s" (aux.t/clj-date->date-time-str created-at))
                   " "
                   message
                   "\n")
              current)) "" db-messages))

(defn asset-news-message
  [asset-news]
  (let [ticket (-> asset-news first :asset-news/ticket name)]
    (str (format "<b>\uD83D\uDCF0 %s NEWS \uD83D\uDCF0</b>\n" ticket)
         "\n"
         (reduce
           (fn [current {:asset-news/keys [txt href datetime]}]
             (let [txt' (-> txt
                            (str/replace ticket "")
                            (str/replace "(" "")
                            (str/replace ")" "")
                            (str/replace ":" ""))]
               (str current
                    "\n"
                    (format "<a href='%s'>%s</a>" href txt')
                    (format "⌚ %s" datetime))))
           "" asset-news))))

(defn asset-price-changed-message
  [{:asset/keys [ticket]
    current-price :asset.market-price/price
    current-date :asset.market-price/price-date}
   {:keys [price date]}
   change-percentage]
  (str (format "<b>\uD83D\uDCF0 %s PRICE CHANGED \uD83D\uDCF0</b>\n" ticket)
       "\n\n"
       (format "<b>%5s</b> FROM R$ %-9s TO R$ %-9s"
               (format "%.2f%%" change-percentage)
               (format "%.2f" current-price)
               (format "%.2f" price))
       "\n"
       (format "CURRENT PRICE FROM DATE : %11s"
               (name current-date))
       "\n"
       (format "NEW PRICE FROM DATE : %11s"
               (name date))))
