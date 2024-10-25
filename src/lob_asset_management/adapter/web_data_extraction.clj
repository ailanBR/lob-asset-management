(ns lob-asset-management.adapter.web-data-extraction
  (:require [clojure.string :as string]
            [lob-asset-management.aux.money :as aux.m]
            [lob-asset-management.aux.time :as aux.t]
            [lob-asset-management.db.asset :as db.a]
            [net.cgrand.enlive-html :as html]))

(defn remove-empty
  [list]
  (filter map? list))

(defn ->price
  [data]
  (-> data
      (html/select [:div.price-block])
      first
      :content
      remove-empty
      first
      :content
      first
      aux.m/safe-number->bigdec)

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

(defn updated-at-date-format?
  [updated-at]
  (= (count (string/split (str updated-at) #" ")) 3))

(defn ->date
  [data]
  (let [updated-at (->> [:div.last-updated]
                        (html/select data)
                        first
                        :content
                        (remove #(or (string/includes? % "\n")
                                     (= % " ")))
                        second
                        :content
                        first)]
    (if (updated-at-date-format? updated-at)
      (aux.t/str-date->date-keyword updated-at)
      (aux.t/current-date->keyword))))

(defn response->internal
  [response]
  (if (not-empty (html/select response [:span.cur-price]))
    (let [price (-> response ->price bigdec)
          date-keyword (->date response)
          historic {date-keyword price}]
      {:price      price
       :date       date-keyword
       :updated-at (aux.t/get-millis)
       :historic   historic})
    {:error "response->internal error extracting data"}))

(defn in-ticket->out-ticket
  [{:asset/keys [ticket type]}]
  (let [asset-name (name ticket)
        {:keys [exchange]} (db.a/get-fixed-info-by-ticket ticket)]
    (if exchange
      (str (name exchange) "/" (string/upper-case asset-name))
      (case type
        (or :stockBR :fii)
        (str "bovespa/" (string/upper-case asset-name))

        :stockEUA
        (str "NASDAQ/" (string/upper-case asset-name))

        :crypto
        (str "coin/" (string/upper-case asset-name) "BRL")))))

(defn advfn-url
  [ticket]
  (str "https://www.advfn.com/stock-market/" ticket "/stock-price"))

;========================== BR ADVFN

(defn- f-content [d] (-> d first :content))
(defn- l-content [d] (-> d last :content))
(defn- s-content [d] (-> d second :content))

(defn br-advfn-url
  [ticket]
  (str "https://br.advfn.com/bolsa-de-valores/" ticket "/cotacao"))

(defn br-historic-advfn-url
  ([ticket]
   (str "https://br.advfn.com/bolsa-de-valores/" ticket "/historico/mais-dados-historicos"))
  ([ticket date-ini date-end]
   (if (and date-ini date-end)
     (str (br-historic-advfn-url ticket) "?Date1=" date-ini "&Date2=" date-end)
     (br-historic-advfn-url ticket))))


(defn asset-news
  [data]
  (->> (html/select data [:div.news-item])
       (map (fn [news]
              {:txt      (-> (html/select news [:div.news-content]) f-content f-content f-content first)
               :from     (-> (html/select news [:div.news-time-content]) f-content first)
               :href     (-> (html/select news [:div.news-content]) f-content f-content first :attrs :href)
               :datetime (-> (html/select news [:div.news-date-content]) f-content first)}))))

(defn br-date->date-keyword
  [data]
  (let [updated-at (->> [:div.last-updated]
                        (html/select data)
                        f-content
                        (remove #(or (string/includes? % "\n")
                                     (= % " ")))
                        l-content
                        first)]
    (if (updated-at-date-format? updated-at)
      (aux.t/str-br-date->date-keyword updated-at)
      (aux.t/current-date->keyword))))

(defn br-response->internal
  [response]
  (if (not-empty (html/select response [:div.price-block]))
    (let [news (asset-news response)
          price (->price response)
          date-keyword (br-date->date-keyword response)
          historic {date-keyword price}]
      {:price      price
       :date       date-keyword
       :updated-at (aux.t/get-millis)
       :historic   historic
       :news       news})
    {:error "response->internal error extracting data"}))

(defn historic-response->internal
  [resp]
  (->> [:table.histo-results]
       (html/select resp)
       f-content
       (remove #(string/includes? % "\n"))
       rest
       (map (fn [row]
              (let [cnt (->> row :content (remove #(string/includes? % "\n")))
                    dt (-> cnt f-content first aux.t/str-br-date->date-keyword)
                    p (-> cnt s-content first aux.m/safe-number->bigdec)]
                {:date dt :price p})))
       (reduce #(assoc %1 (:date %2) (:price %2)) {})))

(defn br-historic-response->internal
  [response]
  (if (not-empty (html/select response [:span.cur-price]))
    (let [historic (historic-response->internal response)
          price (-> (html/select response [:span.cur-price]) f-content first aux.m/safe-number->bigdec)
          date-keyword (br-date->date-keyword response)]
      {:price      price
       :date       date-keyword
       :updated-at (aux.t/get-millis)
       :historic   historic})
    {:error "response->internal error extracting data historic"}))

(comment
  (def r (lob-asset-management.io.http_in/advfn-data-extraction-br {:asset/ticket :tots3
                                                                    :asset/type :stockBR}))

  (html/select r #{[:div.price-block]})

  (br-response->internal r)

  (->price r)                                               ;DONE
  (asset-news r)
  (br-date->date-keyword r)

  )
