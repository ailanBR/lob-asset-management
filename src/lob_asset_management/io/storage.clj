(ns lob-asset-management.io.storage
  (:require  [fire.core :as fire]
             [fire.auth :as auth]
             [clojure.string :as str]
             [environ.core :refer [env]]
             [cheshire.core :as json]
             [lob-asset-management.relevant :as relevant]
             [clojure.java.io :as io]
             [clj-http.client :as http]
             )
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

(comment
  (def google-ctx (-> relevant/secrets :google-key))
  (def google-aut (-> relevant/secrets :google-key))
  (def google-ctx-json (-> relevant/secrets :google-key json/generate-string))
  (def google-aut (with-open [in (io/reader "./resources/oauth.json")]
                       (slurp in)))
  ;----------------------------------------------------------
  (defn sign [claims' priv-key]
    (let [^Base64$Encoder b64encoder (. Base64 	getUrlEncoder)
          ^Signature sig (Signature/getInstance "SHA256withRSA")
          strip (fn [s] (str/replace s "=" ""))
          encode (fn [b] (strip (.encodeToString b64encoder (.getBytes ^String b "UTF-8"))))
          rencode (fn [b] (strip (.encodeToString b64encoder ^"[B" b)))
          header "{\"alg\":\"RS256\"}"
          claims (json/encode claims')
          jwtbody (str (encode header) "." (encode claims))]
      (.initSign sig priv-key)
      (.update sig (.getBytes ^String jwtbody "UTF-8"))
      (str jwtbody "." (rencode (.sign sig)))))

  (def aud "https://oauth2.googleapis.com/token")
  (def scopes SheetsScopes/SPREADSHEETS_READONLY)
  (def t (quot (inst-ms (java.util.Date.)) 1000))
  (def claims {:iss (:client_email google-ctx) :scope scopes :aud aud :iat t :exp (+ t 3599)})

  (defn str->private-key [keystr']
    (let [^Base64$Decoder b64decoder (. Base64 getDecoder)
          ^KeyFactory kf (KeyFactory/getInstance "RSA")
          ^String keystr (-> keystr' (str/replace "\n" "") (str/replace "-----BEGIN PRIVATE KEY-----" "") (str/replace "-----END PRIVATE KEY-----" ""))]
      (->> keystr
           (.decode b64decoder)
           (PKCS8EncodedKeySpec.)
           (.generatePrivate kf))))

  (def private-key (-> google-ctx :private_key str->private-key))
  (def token (sign claims private-key))

  (def body
    (str "grant_type="
         (URLEncoder/encode "urn:ietf:params:oauth:grant-type:jwt-bearer")
         "&assertion=" token
         "&access_type=offline"))

  (http/post aud {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                  :body body})
  ; WORKED WELL
  ;----------------------------------------------------------
  ; private static final List<String> SCOPES =
  ;      Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);
  (def ^:dynamic scope [SheetsScopes/SPREADSHEETS_READONLY])

  ;private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  (def ^:dynamic JSON-FACTORY (Utils/getDefaultJsonFactory))

  ;GoogleClientSecrets clientSecrets =
  ;        GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
  (defn credential-from-json-stream
    "Consumes an input stream containing JSON describing a Google API credential
    `stream` can be anything that can be handled by `clojure.java.io/reader`"
    [stream]
    (with-open [input-stream (io/reader stream)]
      (GoogleClientSecrets/load JSON-FACTORY input-stream)))

  (defn credential-from-json
    "Builds a GoogleCredential from a raw JSON string describing a Google API credential"
    [cred-json]
    (let [charset (Charset/forName "UTF-8")
          byte-array (.getBytes cred-json charset)
          input-stream (new ByteArrayInputStream byte-array)]
      (credential-from-json-stream input-stream)))

  (def ^:dynamic ^GoogleClientSecrets client-secrets (credential-from-json google-aut))

  ;GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
  ;        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
  ;        .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
  ;        .setAccessType("offline")
  ;        .build();
  (def http-transport (GoogleNetHttpTransport/newTrustedTransport))
  (.isMtls http-transport)

  (def flow (->
             (GoogleAuthorizationCodeFlow$Builder. http-transport JSON-FACTORY (:client_id google-ctx) (:private_key google-ctx) scope)
             (.setAccessType "offline")
             (.build)))

  (def flow (->
              (GoogleAuthorizationCodeFlow$Builder. http-transport JSON-FACTORY client-secrets scope)
              (.setAccessType "offline")
              (.build)))
  ;LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
  ;    return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

  (def receiver (-> (LocalServerReceiver$Builder.)
                    (.setPort 8888)
                    (.build)))

  (def authorization (->
                       (AuthorizationCodeInstalledApp. flow receiver)
                       (.authorize "user")))


  ;final String spreadsheetId = "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms";
  (def spread-sheet-id "1_u2Qt64H4K8mPlxCnwORcWuQ90uy7OHtB61oyqHYAto")
  ;final String range = "Class Data!A2:E";
  ;(def range "Home_NEW!D7") ;=> Total invested
  (def range "EXPORT_Stock!A2:C5")
  ;Sheets service =
  ;        new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
  ;            .setApplicationName(APPLICATION_NAME)
  ;            .build();
  (def application "Lob Asset Management OAuth")
  (def service (->
                 (Sheets$Builder. http-transport JSON-FACTORY authorization)
                 (.setApplicationName application)
                 (.build)))
  ;
  ;ValueRange response = service.spreadsheets().values()
  ;.get(spreadsheetId, range)
  ;.execute();
  (def response (-> service (.spreadsheets) (.values) (.get spread-sheet-id range) (.execute)))

  )


















(comment

  (def sg (-> relevant/secrets :google-key))
  (def jg (-> relevant/secrets :google-key json/generate-string))

  (json/generate-string sg)

  ;-----------------------------------------------------------
  (def auth (auth/create-token "GOOGLE_APPLICATION_CREDENTIALS"))

  (println (System/getenv "GOOGLE_APPLICATION_CREDENTIALS"))
  (def t (System/getenv "GG_J"))

  (defn clean-env-var [env-var]
    (-> env-var
        (name)
        (str)
        (str/lower-case)
        (str/replace "_" "-")
        (str/replace "." "-")
        (keyword)))

  (defn decode [json-string]
    (json/decode json-string true))

  (-> "GG_J" clean-env-var env)
  )
