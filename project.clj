(defproject com.github.nubank/morse "master-SNAPSHOT"
  :description "A graphical, interactive tool for browsing Clojure data"
  :dependencies [[cljfmt/cljfmt "0.8.0" :exclusions [org.clojure/clojurescript]]
                 [org.clojure/core.async "1.5.648"]
                 [com.github.clojure/data.alpha.replicant-client "v2023.10.06.01"]
                 [org.openjfx/javafx-fxml     "19.0.2.1"]
                 [org.openjfx/javafx-controls "19.0.2.1"]
                 [org.openjfx/javafx-swing    "19.0.2.1"]
                 [org.openjfx/javafx-base     "19.0.2.1"]
                 [org.openjfx/javafx-web      "19.0.2.1"]
                 [org.yaml/snakeyaml "1.23"]]
  :repositories [["jitpack" "https://jitpack.io"]])
