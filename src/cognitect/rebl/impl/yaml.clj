;;   Copyright (c) Nu North America, Inc. All rights reserved.

(ns cognitect.rebl.impl.yaml
  (:require [clojure.java.io :as io])
  (:import [org.yaml.snakeyaml Yaml]))

(set! *warn-on-reflection* true)

(defn load-all
  [streamable]
  (with-open [is (io/input-stream streamable)]
    (into [] (iterator-seq (.iterator (.loadAll (Yaml.) is))))))


