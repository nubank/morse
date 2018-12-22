;; Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.impl.file
  (:import java.io.File)
  (:require [clojure.java.io :as io]
            [clojure.core.protocols :as p]
            [clojure.datafy :as datafy]))

(defn datafy-node
  [^File f]
  {:name (.getName f)
   :path (.getPath f)
   :modified (.lastModified f)})

(defn bounded-slurp
  "Like slurp, but only up to approximately limit chars."
  [f limit]
  (let [sw (java.io.StringWriter.)]
    (with-open [rdr (io/reader f)]
      (let [^"[C" buffer (make-array Character/TYPE 8192)]
        (loop [n 0]
          (when (< n limit)
            (let [size (.read rdr buffer)]
              (when (pos? size)
                (do
                  (.write sw buffer 0 size)
                  (recur (+ n size)))))))))
    (.toString sw)))

;; should use canonical path
(defn datafy-file
  [^File f]
  (with-meta 
    (assoc (datafy-node f)
      :length (.length f))
    {`p/nav (fn [f k v]
              (if (= :contents k)
                (bounded-slurp (:path f) (* 1024 1024))
                v))}))

(defn datafy-directory
  [^File f]
  (assoc (datafy-node f)
    :name (.getName f)
    :path (.getPath f)
    :files (into [] (.listFiles f))))

(extend-protocol p/Datafiable
  java.io.File
  (datafy [f]
          (cond
           (.isFile f) (datafy-file f)
           (.isDirectory f) (datafy-directory f)
           :default f)))

(defn datafied-file?
  [x]
  (= 'java.io.File (-> x meta ::datafy/class)))



