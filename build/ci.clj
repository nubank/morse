;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns ci (:require [datomic.deps.project :as project]))
(project/load-nses)

(def project
  (project/create {:lib 'com.datomic/rebl
                   :version (str "0.9." (git/revision))
                   :namespace 'cognitect.rebl
                   :instrument? #(str/starts-with? (namespace %) "cognitect")
                   :project-class? #(str/starts-with? % "cognitect/rebl")}))

(defn local-build
  [project]
  (project/clean project)
  (project/test project)
  (project/compile-clj project)
  (project/write-pom project)
  (project/write-jar project))

(defn build
  [project]
  (local-build project)
  (project/deploy project))

(defn -main [& _] (project/main build project))

(comment
  (ci/local-build ci/project)
  )