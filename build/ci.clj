;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns ci (:require [datomic.deps.project :as project]))
(project/load-nses)

(def project
  (project/create {:lib 'com.cognitect/rebl
                   :version (str "0.9." (git/revision))
                   :namespace 'cognitect.rebl
                   :instrument? #(str/starts-with? (namespace %) "cognitect")
                   :project-class? #(str/starts-with? % "cognitect/rebl")}))

(defn write-zip
  "Write a zip file next to local-jar-path, containing the jar plus
the contents of zip-static directory."
  [{:keys [local-jar-path]}]
  (let [jar-name (.getName (io/file local-jar-path))
        zip-path (str/replace local-jar-path ".jar" ".zip")]
    (with-open [zo (zip/output-stream zip-path)]
      (zip/add-file-entry zo jar-name local-jar-path)
      (zip/add-dir zo nil "zip-static"))))

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
  (write-zip project)
  (project/deploy project))

(defn -main [& _] (project/main build project))

(comment
  (ci/local-build ci/project)
  )
