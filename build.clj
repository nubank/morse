(ns build
  (:require [clojure.tools.build.api :as build]))

(def params
  (let [version (build/git-version "1.2.%s")]
    #:build{:dir "target1"                                       ;; clean
            :lib 'cognitect/REBL
            :version version
            :compile-dir "target1/classes"
            :clj-paths ["src"]                                   ;; compile-clj
            :ns-compile '[cognitect.rebl.ui
                          cognitect.rebl.fx
                          cognitect.rebl.charts]
            :filter-nses '[cognitect.rebl]                       ;; compile-clj
            :opts {:elide-meta [:doc :file :line]}               ;; compile-clj
            :src-pom "pom.xml"                                   ;; sync-pom
            :jar-file (format "target1/REBL-%s.jar" version)     ;; jar
            :copy-specs [{:from "resources" :include "**"}]      ;; copy
            :zip-file (format "target1/REBL-%s.zip" version)     ;; zip
            :zip-paths ["deps.edn" "zip-static/**" "target1/REBL-*.jar"] ;; zip
            :build/basis (build/load-basis)}))

(defn clean [args]
  (doto (merge params args) build/clean))

(defn prep [args]
  (doto (merge params args)
    build/clean
    build/compile-clj))

(defn dist [args]
  (doto (merge params args)
    prep
    build/sync-pom
    build/copy
    build/jar
    ;;build/copy
    build/zip))

