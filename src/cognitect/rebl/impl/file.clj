;; Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.impl.file
  (:import
   clojure.lang.LineNumberingPushbackReader
   java.io.File)
  (:require
   [clojure.java.io :as io]
   [clojure.core.protocols :as p]
   [clojure.datafy :as datafy]
   [clojure.edn :as edn]))

(defn node-meta
  [^File f]
  {:rebl.file/path (.getCanonicalPath f)
   :rebl.file/modified (.lastModified f)})

(defn datafy-node
  [^File f]
  (with-meta
    {}
    (node-meta f)))

(comment
  (defmacro time-tap
    [context & body]
    `(let [start# (System/currentTimeMillis)
           result# (do
                     ~@body)]
       (tap> (assoc ~context :msec (- (System/currentTimeMillis) start#)))
       result#)))

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

(defn datafy-file
  [^File f]
  (let [m (datafy-node f)]
    (assoc m
      :length (.length f)
      :name (.getName f))))

(defn datafy-directory
  [^File f]
  (let [m (datafy-node f)]
    (with-meta (map (fn [^File f] (.getName f)) (.listFiles f))
      {`p/nav (fn [_ k v]
                (io/file f v))})))

(defn read-edn
  [^File f]
  (with-open [rdr (LineNumberingPushbackReader. (io/reader f))]
    (let [eof (Object.)]
      (into
       []
       (take-while #(not= % eof))
       (repeatedly #(edn/read {:eof eof} rdr))))))

(def data-file-readers-ref
  (atom {"edn" read-edn}))

(defn- extension
  [s]
  (second (re-matches #".*\.([^.]*)" s )))

(defn data-file-reader
  [^File f]
  (and (.isFile f)
       ;; don't try to read giant data files, for now
       (< (.length f) 10000000) 
       (get @data-file-readers-ref (extension (.getName f)))))

(try
 (let [json-read (requiring-resolve 'clojure.data.json/read)]
   (swap!
    data-file-readers-ref
    assoc
    "json"
    (fn [^File f]
      (with-open [rdr (LineNumberingPushbackReader. (io/reader f))]
        (let [eof (Object.)]
          (into
           []
           (take-while #(not= % eof))
           (repeatedly #(json-read rdr :eof-error? false :eof-value eof))))))))
 (catch java.io.FileNotFoundException _))

(try
 (let [csv-read (requiring-resolve 'clojure.data.csv/read-csv)]
   (swap!
    data-file-readers-ref
    assoc
    "csv"
    (fn [^File f]
      (with-open [rdr (io/reader f)]
        (into
         []
         (csv-read rdr))))))
 (catch java.io.FileNotFoundException _))

(extend-protocol p/Datafiable
  java.io.File
  (datafy [f]
          (cond
           (.isFile f) (if-let [rf (data-file-reader f)]
                         (with-meta (rf f) (node-meta f))
                         (datafy-file f))
           (.isDirectory f) (datafy-directory f)
           :default f)))

(defn datafied-file?
  [x]
  (let [m (meta x)]
    (-> (and (= 'java.io.File (::datafy/class m))
             (:length x))
        boolean)))

(def browsable-extensions-ref
  (atom #{"html" "png"}))

(defn browsable-file?
  [x]
  (let [m (meta x)
        f (::datafy/obj m)]
    (-> (and (instance? File f)
             (:length x)
             (get @browsable-extensions-ref (extension (.getName f))))
        boolean)))

