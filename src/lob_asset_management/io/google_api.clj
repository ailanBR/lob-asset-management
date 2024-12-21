(ns lob-asset-management.io.google_api
  (:require  [fire.core :as fire]
             [fire.auth :as auth]
             [clojure.string :as str]
             [environ.core :refer [env]]
             [cheshire.core :as json]
             [lob-asset-management.relevant :refer [config spread-sheet-config]]
             [mount.core :refer [defstate]]
             [clojure.java.io :as io]
             [clj-http.client :as http])
  (:import
    (com.google.api.client.auth.oauth2 Credential)
    (com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeFlow
                                                  GoogleAuthorizationCodeFlow$Builder
                                                  GoogleClientSecrets)
    (com.google.api.services.sheets.v4 SheetsScopes
                                       Sheets
                                       Sheets$Builder)
    (com.google.api.services.sheets.v4.model AddSheetRequest
                                             AppendCellsRequest
                                             BatchUpdateSpreadsheetRequest
                                             CellData
                                             CellFormat
                                             DeleteDimensionRequest
                                             DimensionRange
                                             ExtendedValue
                                             GridCoordinate
                                             GridProperties
                                             InsertDimensionRequest
                                             NumberFormat
                                             Request
                                             RowData
                                             SheetProperties
                                             UpdateCellsRequest
                                             UpdateSheetPropertiesRequest)
    (com.google.api.client.http HttpTransport
                                HttpRequestInitializer)
    (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
    (com.google.api.client.extensions.jetty.auth.oauth2 LocalServerReceiver$Builder)
    (com.google.api.client.extensions.java6.auth.oauth2 AuthorizationCodeInstalledApp)
    (com.google.api.client.googleapis.util Utils)
    (java.util ArrayList Base64 Base64$Decoder Base64$Encoder)
    (java.io ByteArrayInputStream)
    (java.nio.charset Charset)
    (java.net URLEncoder)
    (java.security KeyFactory Signature)
    (java.security.spec PKCS8EncodedKeySpec)
    ))

;https://developers.google.com/sheets/api/quickstart/java?hl=pt-br
;https://github.com/SparkFund/google-apps-clj/blob/develop/src/google_apps_clj/google_sheets_v4.clj <- Maybe use that library

(def ^:dynamic JSON-FACTORY (Utils/getDefaultJsonFactory))

(defn credential-from-json-stream
  "Consumes an input stream containing JSON describing a Google API credential
  `stream` can be anything that can be handled by `clojure.java.io/reader`"
  [stream]
  (with-open [input-stream (io/reader stream)]
    (GoogleClientSecrets/load JSON-FACTORY input-stream)))

(defn get-google-aut
  [oauth-path]
  (with-open [in (io/reader oauth-path)]
    (slurp in)))

(defn credential-from-json
  "Builds a GoogleCredential from a raw JSON string describing a Google API credential"
  [cred-json]
  (let [charset (Charset/forName "UTF-8")
        byte-array (.getBytes cred-json charset)
        input-stream (new ByteArrayInputStream byte-array)]
    (credential-from-json-stream input-stream)))

(defn ^:dynamic ^GoogleClientSecrets get-client-secrets
  [oauth-path]
  (-> (get-google-aut oauth-path)
      (credential-from-json)))

(defn get-flow
  []
  (let [http-transport (GoogleNetHttpTransport/newTrustedTransport)
        json-factory (Utils/getDefaultJsonFactory)
        client-secrets (get-client-secrets (:oauth-path spread-sheet-config))
        scope [SheetsScopes/SPREADSHEETS_READONLY]]
    (-> (GoogleAuthorizationCodeFlow$Builder. http-transport json-factory client-secrets scope)
        (.setAccessType "offline")
        (.build))))

(defn get-receiver
  ([]
   (get-receiver 8888))
  ([port]
   (-> (LocalServerReceiver$Builder.)
       (.setPort port)
       (.build))))

(defn get-authorization
  []
  (let [flow (get-flow)
        receiver (get-receiver)]
    (-> (AuthorizationCodeInstalledApp. flow receiver)
        (.authorize "user"))))

(defn get-service
  []
  (let [http-transport (GoogleNetHttpTransport/newTrustedTransport)
        json-factory (Utils/getDefaultJsonFactory)
        authorization (get-authorization)
        application "Lob Asset Management OAuth"]
    (->
      (Sheets$Builder. http-transport json-factory authorization)
      (.setApplicationName application)
      (.build))))

(defstate spread-sheet-service :start (get-service))

(defn get-by-index
  [i]
  (let [release (->> config
                     :releases
                     second
                     :stock
                     :columns)]
    (->> (sort release)
         (map-indexed list)
         (filter #(= (first %) i))
         first
         second
         second)))

(defn values->indexed-map
  [l i res]
  (if (seq l)
    (->>
      (assoc res i (first l))
      (values->indexed-map (rest l) (+ i 1)))
    res))

(defn spread-sheet-out->in
  [response]
  (->> (map (fn [k] {(keyword (key k)) (val k)}) response)
       (reduce conj)
       :values
       (map (fn [l]
              (->> (values->indexed-map l 0 {})
                   (map (fn [ln]
                          {(get-by-index (first ln)) (second ln)}))
                   (reduce merge))))))

(defn get-range
  ([range]
   (get-range (:spread-sheet-id spread-sheet-config) range))
  ([spread-sheet-id range]
   (-> spread-sheet-service
       (.spreadsheets)
       (.values)
       (.get spread-sheet-id range)
       (.execute)
       spread-sheet-out->in)))

(comment
  (get-range "EXPORT_Stock!A2:I10")

  (def c {:columns        {:A :type
                           :B :transaction-date
                           :C :movement-type
                           :D :product
                           :E :exchange
                           :F :quantity
                           :G :unit-price
                           :H :operation-total
                           :I :currency}})

  (def xc (:columns c))

  (get xc 1)

  (def tl ["Credito" "27/04/2021" "Transferência - Liquidação" "AAPL" "Sproutfy" "0,12202170" "$155,71" "$19,00" "UST"])

  (map-l tl 0 {})

  (map-indexed list (sort xc))

  (get-by-index 0)

  (->> (get-range "EXPORT_Stock!A2:I10")
       :values
       (map (fn [l]
              (->> (map-l l 0 {})
                   (map (fn [ln]
                          {(get-by-index (first ln)) (second ln)}))
                   (reduce merge)))))

  (lob-asset-management.adapter.asset/movements->assets
    (get-range "EXPORT_Stck!A2:I10")))
