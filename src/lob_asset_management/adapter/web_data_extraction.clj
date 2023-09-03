(ns lob-asset-management.adapter.web-data-extraction
  (:require [lob-asset-management.aux.money :as a.m]
            [lob-asset-management.aux.time :as aux.t]
            [net.cgrand.enlive-html :as html]))

(defn ->price
  [data]
  (-> data
      (html/select [:span.cur-price])
      first
      :content
      first)

  #_(->> [:div.market-header-values]
       (html/select data)
       first
       :content
       (remove #(clojure.string/includes? % "\n"))
       first
       :content
       (remove #(clojure.string/includes? % "\n"))
       second
       :content
       first
       a.m/safe-number->bigdec)
  )

(defn ->txt-panel
  [data]
  (-> data
      (html/select [:div.panel-default])
      (nth 5)
      :content))

(defn ->date
  [data]
  (->> [:div.last-updated]
       (html/select data)
       first
       :content
       (remove #(or (clojure.string/includes? % "\n")
                    (= % " ")))
       second
       :content
       first
       aux.t/str-date->clj-date))

(defn response->internal
  [response]
  (if (not-empty (html/select response [:span.cur-price]))
    (let [price (-> response ->price bigdec)
          date-keyword (->date response)
          historic {date-keyword price}]
      {:price      price
       :date       date-keyword
       :updated-at (aux.t/get-current-millis)
       :historic   historic})
    {:error "response->internal error extracting data"}))

(defn in-ticket->out-ticket
  [{:asset/keys [ticket type]}]
  (let [asset-name (name ticket)]
    (if (or (= type :stockBR) (= type :fii))
      (str "bovespa/" (clojure.string/upper-case asset-name))
      (str "NASDAQ/" (clojure.string/upper-case asset-name)))))

(defn advfn-url
  [ticket]
  (str "https://www.advfn.com/stock-market/" ticket "/stock-price"))
