(ns lob-asset-management.models.file
  (:require [schema.core :as s]))

(def list-file-name [:portfolio :transaction :asset :read-release])
(s/defschema file-name (s/enum :portfolio :transaction :asset :read-release))