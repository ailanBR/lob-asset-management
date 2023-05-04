(ns lob-asset-management.models.file
  (:require [schema.core :as s]))

(def list-file-name #{:portfolio :transaction :asset :read-release})
(s/defschema file-name (apply s/enum list-file-name))