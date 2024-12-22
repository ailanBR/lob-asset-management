(ns lob-asset-management.io.google_api
  (:require  [lob-asset-management.adapter.google-api :as a.google-api]
             [lob-asset-management.relevant :refer [config spread-sheet-config]]
             [mount.core :refer [defstate]]
             [clojure.java.io :as io]
             [schema.core :as s])
  (:import
    (com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeFlow$Builder
                                                  GoogleClientSecrets)
    (com.google.api.services.sheets.v4 SheetsScopes
                                       Sheets$Builder)
    (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
    (com.google.api.client.extensions.jetty.auth.oauth2 LocalServerReceiver$Builder)
    (com.google.api.client.extensions.java6.auth.oauth2 AuthorizationCodeInstalledApp)
    (com.google.api.client.googleapis.util Utils)
    (java.util ArrayList Base64 Base64$Decoder Base64$Encoder)
    (java.io ByteArrayInputStream)
    (java.nio.charset Charset)
    ))

;https://developers.google.com/sheets/api/quickstart/java?hl=pt-br
;https://github.com/SparkFund/google-apps-clj/blob/develop/src/google_apps_clj/google_sheets_v4.clj <- Maybe use that library

(def ^:dynamic JSON-FACTORY (Utils/getDefaultJsonFactory))

(defn- credential-from-json-stream
  "Consumes an input stream containing JSON describing a Google API credential
  `stream` can be anything that can be handled by `clojure.java.io/reader`"
  [stream]
  (with-open [input-stream (io/reader stream)]
    (GoogleClientSecrets/load JSON-FACTORY input-stream)))

(defn- get-google-aut
  [oauth-path]
  (with-open [in (io/reader oauth-path)]
    (slurp in)))

(defn- credential-from-json
  "Builds a GoogleCredential from a raw JSON string describing a Google API credential"
  [cred-json]
  (let [charset (Charset/forName "UTF-8")
        byte-array (.getBytes cred-json charset)
        input-stream (new ByteArrayInputStream byte-array)]
    (credential-from-json-stream input-stream)))

(defn- ^:dynamic ^GoogleClientSecrets get-client-secrets
  [oauth-path]
  (-> (get-google-aut oauth-path)
      (credential-from-json)))

(defn- get-flow
  []
  (let [http-transport (GoogleNetHttpTransport/newTrustedTransport)
        json-factory (Utils/getDefaultJsonFactory)
        client-secrets (get-client-secrets (:oauth-path spread-sheet-config))
        scope [SheetsScopes/SPREADSHEETS_READONLY]]
    (-> (GoogleAuthorizationCodeFlow$Builder. http-transport json-factory client-secrets scope)
        (.setAccessType "offline")
        (.build))))

(defn- get-receiver
  ([]
   (get-receiver 8888))
  ([port]
   (-> (LocalServerReceiver$Builder.)
       (.setPort port)
       (.build))))

(defn- get-authorization
  []
  (let [flow (get-flow)
        receiver (get-receiver)]
    (-> (AuthorizationCodeInstalledApp. flow receiver)
        (.authorize "user"))))

(defn- get-service
  []
  (let [http-transport (GoogleNetHttpTransport/newTrustedTransport)
        json-factory (Utils/getDefaultJsonFactory)
        authorization (get-authorization)
        application "Lob Asset Management OAuth"]
    (->
      (Sheets$Builder. http-transport json-factory authorization)
      (.setApplicationName application)
      (.build))))

(defstate ^{:on-reload :noop} spread-sheet-service :start (get-service))

(s/defn get-range
  ([range :- s/Str]
   (get-range (:spread-sheet-id spread-sheet-config) range))
  ([spread-sheet-id range]
   (let [columns-config (->> config :releases second :stock :columns)]
     (-> spread-sheet-service
         (.spreadsheets)
         (.values)
         (.get spread-sheet-id range)
         (.execute)
         (a.google-api/spread-sheet-out->in columns-config)))))

(comment
  (get-range "EXPORT_Stock!A2:I10")

  (lob-asset-management.adapter.asset/movements->assets
    (get-range "EXPORT_Stock!A2:I10"))


  )
