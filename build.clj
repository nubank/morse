(ns build
  (:refer-clojure :exclude [compile])
  (:require [clojure.tools.build.api :as b]))

(def lib 'cognitect/REBL)
(def version (format "1.2.%s" (b/git-count-revs nil)))
(def jar-file (format "target1/REBL-%s.jar" version))
(def class-dir "target1/classes")
(def basis (b/load-basis nil))

(defn clean [args]
  (b/delete {:path "target1"}))

(defn compile [args]
  (clean args)
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir
                  :compiler-opts (merge {:elide-meta [:doc :file :line]}
                                   (:compiler-opts args))
                  :ns-compile '[cognitect.rebl.ui
                                cognitect.rebl.fx
                                cognitect.rebl.charts]
                  :filter-nses '[cognitect.rebl]})
  (b/copy {:target-dir class-dir
           :src-dirs ["resources"]}))

(defn jar [args]
  (compile args)
  (b/sync-pom {:lib lib
               :version version
               :basis basis
               :class-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

;; clj -X:build dist
(defn dist [args]
  (jar args)
  (b/copy {:target-dir "target1/zip"
           :src-dirs ["target1"]
           :include "REBL-*.jar"})
  (b/copy {:target-dir "target1/zip"
           :src-dirs ["zip-static"]})
  (b/zip {:src-dirs ["target1/zip"]
          :zip-file (format "target1/REBL-%s.zip" version)}))
