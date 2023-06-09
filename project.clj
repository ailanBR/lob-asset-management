(defproject lob-asset-management "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[alekcz/fire "0.5.0"]
                 [cheshire "5.10.1"]
                 [clojure.java-time "1.2.0"]
                 [clj-time "0.15.2"]
                 [clj-http "3.12.3"]
                 [dk.ative/docjure "1.19.0"]
                 [environ "1.2.0"]
                 ;[log4j "2.17.2"]
                 [nubank/matcher-combinators "3.8.4"]
                 [mount "0.1.17"]
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/tools.cli "1.0.219"]
                 [org.clojure/tools.logging "1.2.4"]
                 [prismatic/schema "1.4.1"]
                 [telegrambot-lib "2.3.0"]]
  :repl-options {:init-ns lob-asset-management.core
                 :width 12000}
  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/jul-factory"]
  :main ^:skip-aot lob-asset-management.core)
