(ns lob-asset-management.aux.file
  (:require [clojure.java.io :as io]))

(defn file-exists? [path]
  (.exists (io/file path)))
