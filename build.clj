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
            :build/basis (build/load-basis)}))

(defn clean [args]
  (doto (merge params args) build/clean))

(defn prep [args]
  (doto (merge params args)
    build/clean
    build/compile-clj))

(defn dist [args]
  (let [merged (merge params args)]
    (doto merged
      prep
      build/sync-pom
      build/copy
      build/jar)
    (doto (merge merged
            #:build{:build/compile-dir "target1/zip"
                    :copy-specs [{:from "target1" :include "REBL-*.jar"}
                                 {:from "zip-static" :include "**"}]
                    :zip-paths ["target1/zip"]
                    :zip-file (format "target1/REBL-%s.zip" (:build/version merged))})
      build/copy
      build/zip)))
