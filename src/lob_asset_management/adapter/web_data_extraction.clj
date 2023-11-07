(ns lob-asset-management.adapter.web-data-extraction
  (:require [lob-asset-management.aux.money :as aux.m]
            [lob-asset-management.aux.time :as aux.t]
            [net.cgrand.enlive-html :as html]))

(defn ->price
  [data]
  (-> data
      (html/select [:span.cur-price])
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
  (= (count (clojure.string/split (str updated-at) #" ")) 3))

(defn ->date
  [data]
  (let [updated-at (->> [:div.last-updated]
                        (html/select data)
                        first
                        :content
                        (remove #(or (clojure.string/includes? % "\n")
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
  (let [asset-name (name ticket)]
    (if (or (= type :stockBR) (= type :fii))
      (str "bovespa/" (clojure.string/upper-case asset-name))
      (str "NASDAQ/" (clojure.string/upper-case asset-name)))))

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
  [ticket]
  (str "https://br.advfn.com/bolsa-de-valores/" ticket "/historico/mais-dados-historicos"))

(defn asset-news
  [data]
  (->> [:table#id_news]
       (html/select data)
       f-content
       (remove #(clojure.string/includes? % "\n"))
       (map #(let [cnt (:content %)
                   dt (-> cnt f-content first)
                   hr (-> cnt s-content first)
                   from (-> cnt rest rest f-content f-content first)
                   txt (-> cnt l-content f-content first)
                   href (clojure.string/join ["https:" (-> cnt l-content first :attrs :href)])]
               {:id   (-> "-"
                          (clojure.string/join [dt hr from])
                          (clojure.string/replace #" " "-")
                          (clojure.string/replace #"/" "-")
                          (clojure.string/replace #":" "-"))
                :txt  txt
                :datetime (clojure.string/join " " [dt hr])
                :href href}))
       rest))

(defn br-date->date-keyword
  [data]
  (let [updated-at (->> [:div.last-updated]
                        (html/select data)
                        f-content
                        (remove #(or (clojure.string/includes? % "\n")
                                     (= % " ")))
                        l-content
                        first)]
    (if (updated-at-date-format? updated-at)
      (aux.t/str-br-date->date-keyword updated-at)
      (aux.t/current-date->keyword))))

(defn br-response->internal
  [response]
  (if (not-empty (html/select response [:span.cur-price]))
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
       (remove #(clojure.string/includes? % "\n"))
       rest
       (map (fn [row]
              (let [cnt (->> row :content (remove #(clojure.string/includes? % "\n")))
                    dt (-> cnt f-content first aux.t/str-br-date->date-keyword)
                    p (-> cnt s-content first aux.m/safe-number->bigdec)]
                {:date dt :price p})))
       (reduce #(assoc %1 (:date %2) (:price %2)) {})))

(defn br-historic-response->internal
  [response]
  (if (not-empty (html/select response [:span.cur-price]))
    (let [historic (historic-response->internal response)
          price (->price response)
          date-keyword (br-date->date-keyword response)]
      {:price      price
       :date       date-keyword
       :updated-at (aux.t/get-millis)
       :historic   historic})
    {:error "response->internal error extracting data historic"}))

(comment
  (def r (lob-asset-management.io.http_in/advfn-data-extraction-br {:asset/ticket :abev3
                                                                    :asset/type :stockBR}))

  (html/select r [:span.cur-price])
  (br-response->internal r)

  )
