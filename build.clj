(require '[clojure.tools.build :as build])

(build/build
  '{:params
    {:build/target-dir "target1"                                ;; clean
     :build/compile-dir "target1/classes"                       ;; copy, jar, compile-clj, sync-pom
     :build/jar-file "target/rebl.jar"                          ;; jar
     :build/clj-paths ["src"]                                   ;; compile-clj
     :build/filter-nses [cognitect.rebl]                        ;; compile-clj
     :build/copy-specs [{:from ["resources"]}]                  ;; copy
     :build/src-pom "pom.xml"                                   ;; sync-pom
     :build/version "???"                                       ;; sync-pom, Q: where to get version?
     :build/lib com.cognitect/REBL                              ;; sync-pom
     :git-version/template "0.9.%s"                             ;; git-version
     :build/zip-dir "target1/zip"}                              ;; WiP
    :tasks [[clean]  ;; :target-dir
            [clojure.tools.build.extra/git-version]
            [compile-clj {:build/opts {:elide-meta [:doc :file :line]}}] ;; :clj-paths, :compile-dir?, :filter-nses
            [copy] ;; :compile-dir?
            [sync-pom] ;; :src-pom, :lib, :version, :compile-dir?
            [jar] ;; :compile-dir, :jar-file, :main
;;            [format-str {:build/template "REBL-%s.zip" :build/args [:flow/version] :build/out> :build/zip-name}]
            [copy {:build/compile-dir :build/zip-dir   ;; WiP
                   :build/copy-specs [{:from ["."] :include "deps.edn"}
                                      {:from ["zip-static"] :include "**" :replace {"VERSION" :flow/version}}
                                      {:from ["zip-static2"] :include "**" :replace {"VERSION" :flow/version}}
                                      {:from ["target1"] :include "REBL-*.jar"}]}]
            [zip] ;; :zip-paths?, :zip-file?
            ]})
