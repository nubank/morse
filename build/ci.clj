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
      :zip-base (str/replace jar-name ".jar" "")
      :local-zip-path (str/replace local-jar-path ".jar" ".zip")
      :install-zip-path (str/join "/" [repo-base
                                       (str/replace lib "." "/")
                                       version
                                       (str/replace jar-name ".jar" ".zip")]))))

(def project
  (create-project {:lib 'com.cognitect/REBL
                   :version (str "0.9." (git/revision))
                   :namespace 'cognitect.rebl
                   :instrument? #(str/starts-with? (namespace %) "cognitect")
                   :project-class? #(str/starts-with? % "cognitect/rebl")}))

(defn example-deps
  [jar-name]
  {:paths []
   :deps {'com.cognitect/rebl-core {:local/root (str "../" jar-name)}}})

(def javafx14-deps
  '{org.openjfx/javafx-fxml     {:mvn/version "14.0.1"}
    org.openjfx/javafx-controls {:mvn/version "14.0.1"}
    org.openjfx/javafx-swing    {:mvn/version "14.0.1"}
    org.openjfx/javafx-base     {:mvn/version "14.0.1"}
    org.openjfx/javafx-web      {:mvn/version "14.0.1"}})

(def javafx15-deps
  '{org.openjfx/javafx-fxml     {:mvn/version "15-ea+6"}
    org.openjfx/javafx-controls {:mvn/version "15-ea+6"}
    org.openjfx/javafx-swing    {:mvn/version "15-ea+6"}
    org.openjfx/javafx-base     {:mvn/version "15-ea+6"}
    org.openjfx/javafx-web      {:mvn/version "15-ea+6"}})

(defn write-zip
  "Write a zip file next to local-jar-path, containing the jar plus
the contents of zip-static directory."
  [{:keys [local-jar-path jar-name local-zip-path zip-base]}]
  (let [base-deps (example-deps jar-name)]
    (binding [*print-namespace-maps* false]
      (with-open [zo (zip/output-stream local-zip-path)]
        (zip/add-entry zo
          (str zip-base "/java8/deps.edn")
          (with-out-str (pp/pprint base-deps)))
        (zip/add-entry zo
          (str zip-base "/openjfx14/deps.edn")
          (with-out-str (pp/pprint (update base-deps :deps merge javafx14-deps))))
        (zip/add-entry zo
          (str zip-base "/openjfx15ea/deps.edn")
          (with-out-str (pp/pprint (update base-deps :deps merge javafx15-deps))))
        (zip/add-file-entry zo (str zip-base "/" jar-name) local-jar-path)
        (zip/add-dir zo zip-base "zip-static")))))

(defn build
  [project]
  (project/clean project)
  (project/test project)
  (project/compile-clj project)
  (project/write-pom project)
  (project/write-jar project)
  (write-zip project))

(defn deploy
  [project]
  (build project)
  (project/deploy project))

(def releases-path
  {:bucket "private-releases-1fc2183a"
   :key "releases/REBL"})

(def manifest-path "releases/REBL/manifest.edn")

(defn release-plan
  [current-project version]
  (let [{:keys [lib install-zip-path] :as project} (create-project (assoc current-project :version version))
        zip-name (.getName (io/file install-zip-path))
        key (str (:key releases-path) "/" zip-name)
        to {:bucket (:bucket releases-path)
          :key key}]
    {:from {:bucket (:repo-bucket project)
            :key install-zip-path}
     :to to
     :manifest {:current to}}))

(defn release
  [current-project release-version]
  (let [plan (release-plan current-project release-version)
        client (s3/client)]
    (pp/pprint {:releasing plan})
    (s3/copy [plan])
    (s3/write-edn (:bucket releases-path) manifest-path
                  (:manifest plan))))

(defn -main [& _] (project/main build project))

(comment
  (ci/build ci/project)
  (ci/release ci/project "0.9.109")

  (ci/release ci/project "0.9.170")
  
  (ci/release-plan ci/project "0.9.108")
  )
