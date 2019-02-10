;;   Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.impl.reflect
  (:import [clojure.lang IPersistentCollection]
           [java.lang.reflect Field Modifier]
           [java.util Map Collection]))

(set! *warn-on-reflection* true)

(defn instance-fields
  [x]
  (let [cls (class x)
        sups (into #{cls} (supers cls))]
    (into
     #{}
     (comp (map (fn [^Class c] (.getDeclaredFields c)))
           cat
           (filter (fn [^Field f]
                     (zero? (bit-and Modifier/STATIC (.getModifiers f))))))
     sups)))

(defn reflect-map
  [x]
  (let [flds (instance-fields x)]
    (into
     {}
     (map (fn [^Field f]
            (.setAccessible f true)
            [(keyword (.getName f)) (.get f x)]))
     flds)))

(defn browsable?
  [inst]
  (let [c (class inst)]
    (and c
         (not (instance? IPersistentCollection c))
         (not (instance? Collection c))
         (not (instance? Map c))
         (not (number? c)))))



