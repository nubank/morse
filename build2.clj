(ns build2
  (:require [clojure.tools.build.api :as build]))

(def lib 'cognitect/REBL)
(def version (build/git-version "1.2.%s"))
(def jar-file (format "target1/REBL-%s.jar" version))
(def classes-dir "target1/classes")
(def basis (build/load-basis)) ;; delay?

(defn clean [args]
  (build/clean (merge {:build.clean/dir "target1"} args)))

(defn prep [args]
  (clean args)
  (build/compile-clj {:project/basis basis
                      :build.compile/paths ["src"]
                      :build.compile/dir classes-dir
                      :build.compile/opts (or (:compiler-opts args) {:elide-meta [:doc :file :line]}) ;; can change
                      :build.compile/nses '[cognitect.rebl.ui
                                            cognitect.rebl.fx
                                            cognitect.rebl.charts]
                      :build.compile/filter-nses '[cognitect.rebl]}))

(defn jar [args]
  (prep args)
  (build/sync-pom {:project/lib lib
                   :project/version version ;; read from args?
                   :project/basis basis
                   :build.pom/src "pom.xml"})
  (build/copy {:build.copy/to classes-dir
               :build.copy/specs [{:from "resources" :include "**"}]})
  (build/jar {:build.jar/from classes-dir
              :build.jar/file jar-file}))

;; clj -X:build dist
(defn dist [args]
  (jar args)
  (build/copy {:build.copy/to "target1/zip"
               :build.copy/specs [{:from "target1" :include jar-file}
                                  {:from "zip-static" :include "**"}]})
  (build/zip {:build.zip/from ["target1/zip"]
              :build.zip/file (format "target1/REBL-%s.zip" (:build/version version))})) ;; read version from args?

;; idea: one function that's a runner?
;; idea: support for repl - load this ns, run function when needed
