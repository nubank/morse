(require 'ci)

ci/project

(require '[clojure.java.io :as io]
         '[cognitect.deps.build.path :as path])
(path/rel "A" "B")
(.getName (io/file "a/b/c"))

(ci/local-build ci/project)
(ci/write-zip ci/project)

(ci/build ci/project)

(class  ci/project)

(ci/release-plan ci/project "0.x.x")

(ci/release-plan ci/project "0.9.99")

(ci/release ci/project "0.9.99")

(in-ns 'cognitect.deps.build.zip)
(in-ns 'user)
