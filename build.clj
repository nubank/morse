(require '[clojure.tools.build.api :as build])

(def params
  (let [version (build/git-version "1.2.%s")
        basis   (build/load-basis)]
    {:build/lib 'cognitect/REBL                                 ;; sync-pom
     :build/compile-dir "target1/classes"                       ;; copy, jar, compile-clj, sync-pom
     :build/jar-file "target1/rebl.jar"                         ;; jar
     :build/clj-paths ["src"]                                   ;; compile-clj
     :build/filter-nses '[cognitect.rebl]                       ;; compile-clj
     :build/copy-specs [{:from ["resources"]}]                  ;; copy
     :build/src-pom "pom.xml"                                   ;; sync-pom
     :build/opts {:elide-meta [:doc :file :line]}
     :build/zip-file  (format "target1/REBL-%s.zip" version)
     :build/zip-paths ["deps.edn" "zip-static/**" "target1/REBL-*.jar"]
     :build/basis basis}))

(defn clean [args]
  (doto (merge params args) build/clean))

(defn compile [args]
  (doto (merge params args)
    build/clean
    build/compile-clj))

(defn dist [args]
  (doto (merge params args)
    compile
    build/copy
    build/sync-pom
    build/jar
    build/copy
    build/zip))

