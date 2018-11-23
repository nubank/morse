;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns ci (:require [datomic.deps.project :as project]))
(project/load-nses)

(defn create-project
  [m]
  (let [{:keys [local-jar-path repo-base version lib ] :as result}
        (project/create m)
        jar-name (.getName (io/file local-jar-path))]
    (assoc result
      :jar-name jar-name
      :local-zip-path (str/replace local-jar-path ".jar" ".zip")
      :install-zip-path (str/join "/" [repo-base
                                       (str/replace lib "." "/")
                                       version
                                       (str/replace jar-name ".jar" ".zip")]))))

(def project
  (create-project {:lib 'com.cognitect/rebl
                   :version (str "0.9." (git/revision))
                   :namespace 'cognitect.rebl
                   :instrument? #(str/starts-with? (namespace %) "cognitect")
                   :project-class? #(str/starts-with? % "cognitect/rebl")}))

(defn write-zip
  "Write a zip file next to local-jar-path, containing the jar plus
the contents of zip-static directory."
  [{:keys [local-jar-path jar-name local-zip-path]}]
  (with-open [zo (zip/output-stream local-zip-path)]
    (zip/add-file-entry zo jar-name local-jar-path)
    (zip/add-dir zo nil "zip-static")))

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

(def releases-path
  {:bucket "private-releases-1fc2183a"
   :key "releases/REBL"})

(def manifest-path "releases/REBL/manifest.edn")

(defn release-plan
  [current-project version]
  (let [{:keys [lib install-zip-path] :as project} (create-project (assoc current-project :version version))
        zip-name (.getName (io/file install-zip-path))
        key (str (:key releases-path) "/" zip-name)]
    {:from {:bucket (:repo-bucket project)
            :key install-zip-path}
     :to {:bucket (:bucket releases-path)
          :key key}}))

(defn release
  [current-project release-version]
  (let [plan (release-plan current-project release-version)
        client (s3/client)]
    (pp/pprint {:releasing plan})
    (s3/copy [plan])
    (s3/write-edn (:bucket releases-path) manifest-path
                  {:latest (:to plan)})))

(defn -main [& _] (project/main build project))

(comment
  (ci/local-build ci/project)
  )
