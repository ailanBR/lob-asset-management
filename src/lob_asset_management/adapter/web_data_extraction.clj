(ns lob-asset-management.adapter.web-data-extraction
  (:require [lob-asset-management.aux.money :as a.m]
            [lob-asset-management.aux.time :as aux.t]
            [net.cgrand.enlive-html :as html]))

(defn ->price
  [data]
  (->> [:div.market-header-values]
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
       a.m/safe-number->bigdec))

(defn ->txt-panel
  [data]
  (-> data
      (html/select [:div.panel-default])
      (nth 5)
      :content))

(defn ->price-perspective
  [data]
  (->> data
       ->txt-panel
       (remove #(clojure.string/includes? % "\n"))
       second
       :content
       (remove #(clojure.string/includes? % "\n"))
       first
       :content
       first))

(defn ->company-overview
  [data]
  (->> data
       ->txt-panel
       (remove #(clojure.string/includes? % "\n"))
       last
       :content
       (remove #(clojure.string/includes? % "\n"))
       first))

(defn response->internal
  [response]
  (if (not-empty (html/select response [:div.market-header-values]))
    {:price            (-> response ->price bigdec)
     :date             (aux.t/current-date->keyword)        ;TODO Get from response
     :updated-at       (aux.t/get-current-millis)
     :perspective      (->price-perspective response)
     :company-overview (->company-overview response)}
    {:error "response->internal error extracting data"}))

(defn in-ticket->out-ticket
  [{:asset/keys [ticket type]}]
  (let [asset-name (name ticket)]
    (if (or (= type :stockBR) (= type :fii))
      (str asset-name ":bs")
      (str asset-name ":us"))))
