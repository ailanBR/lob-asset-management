(ns lob-asset-management.controller.asset
  (:require [clojure.tools.logging :as log]
            [lob-asset-management.db.asset-news :as db.asset-news]
            [lob-asset-management.io.http_in :as io.http]))

(defn update-news
  [{:asset/keys [ticket name]} news]
  (let [asset-news (map (fn [{:keys [id txt datetime href]}]
                          {:asset-news/ticket   ticket
                           :asset-news/name     name
                           :asset-news/id       id
                           :asset-news/txt      txt
                           :asset-news/datetime datetime
                           :asset-news/href     href}) news)]
    (db.asset-news/upsert! asset-news)))

(defn get-stock-real-time
  [{:asset/keys [ticket] :as asset}]
  (if-let [{:keys [news]} (io.http/advfn-data-extraction-br asset)]
    (update-news asset news )
    (log/error (str "ERROR when getting news " ticket))))
