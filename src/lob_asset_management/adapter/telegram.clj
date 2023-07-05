(ns lob-asset-management.adapter.telegram
  (:require [lob-asset-management.logic.portfolio :as l.p]))

(defn portfolio-table-message
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

(defn assets-table-message
  [portfolio]
  (let [number (atom 0)]
    (str "<b>\uD83D\uDCCA Portfolio allocation \uD83D\uDCCA</b>\n"
         "<pre>"
         "|n.|Ticket| Category |  %  | Qnt |\n"
         "|--|:-----|:--------:|-----|-----|\n"
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
                        (format "%5s" (if (< quantity 1)
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
