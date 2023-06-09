;;   Copyright (c) Nu North America, Inc. All rights reserved.

(ns dev.nu.morse.impl.beans
  (:import [clojure.lang IPersistentCollection]
           [java.util Map Collection])
  (:require [clojure.datafy :as datafy]
            [clojure.reflect :as reflect]))

(def skip-package-set
  #{(.getPackage String)})

(defn- bean-class?*
  [c]
  (let [{:keys [bases members]} (datafy/datafy c)
        typesym (and c (@#'reflect/typesym c))
        constructors (get members typesym)]
    (and (contains? bases 'java.io.Serializable)
         (some #(empty? (:parameter-types %)) constructors)
         (not (contains? skip-package-set (.getPackage c))))))

(def bean-class? (memoize bean-class?*))

(defn browsable?
  [inst]
  (let [c (class inst)]
    (and (not (instance? IPersistentCollection inst))
         (not (instance? Collection inst))
         (not (instance? Map inst))
         (bean-class? c))))

