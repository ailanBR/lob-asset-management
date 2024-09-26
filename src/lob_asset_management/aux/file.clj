(ns lob-asset-management.aux.file
  (:require [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [lob-asset-management.aux.util :refer [log-colors]])
  (:import
    (java.util ArrayList Base64 Base64$Decoder Base64$Encoder)
    (java.io ByteArrayInputStream)
    (java.nio.charset Charset)))

(defn file-exists? [path]
  (.exists (io/file path)))

(defn file->edn [file-path]
  (when (file-exists? file-path)
    (with-open [in (io/reader file-path)]
      (-> in slurp edn/read-string))))

(defn edn->file [data file-path]
  (io/make-parents file-path)
  (with-open [out (io/writer file-path)]
    (binding [*out* out]
      (clojure.pprint/pprint data))))

(defn edn->file-table [data file-path]
  (io/make-parents file-path)
  (with-open [out (io/writer file-path)]
    (binding [*out* out]
      (clojure.pprint/print-table data))))

(defn delete
  [file-path]
  (io/delete-file file-path))

(defn valid-xlsx-file?
  [file-path]
  (->> #"\."
       (clojure.string/split file-path)
       (filter #(= % "~lock"))
       first
       boolean
       not))

(defn string->byte-array
  [string]
  (let [charset (Charset/forName "UTF-8")
        byte-array (.getBytes string charset)
        input-stream (new ByteArrayInputStream byte-array)]
    input-stream))

(defn file->byte-array [file-path]
  (try
    (when (file-exists? file-path)
      (with-open [in (io/reader file-path)]
        (-> in slurp string->byte-array)))
    (catch Exception e
      (log/error (str (:fail log-colors)
                      "[file->byte-array] Error when reading "
                      file-path
                      (:end log-colors)))
      "fail")))
