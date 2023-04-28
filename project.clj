(defproject lob-asset-management "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [clojure.java-time "1.2.0"]
                 [dk.ative/docjure "1.19.0"]
                 [prismatic/schema "1.4.1"]
                 [nubank/matcher-combinators "3.8.4"]
                 [clj-http "3.12.3"]
                 [cheshire "5.11.0"]]
  :repl-options {:init-ns lob-asset-management.core})
