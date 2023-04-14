;; Copyright (c) Nu North America, Inc., All rights reserved.

(ns cognitect.rebl.impl.file
  (:import
   clojure.lang.LineNumberingPushbackReader
   java.io.File)
  (:require
   [clojure.java.io :as io]
   [clojure.core.protocols :as p]
   [clojure.datafy :as datafy]
   [clojure.edn :as edn]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

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

(defn read-properties
  [^File f]
  (with-open [is (io/input-stream f)]
    (->> (doto (java.util.Properties.)
           (.load is))
         (into {}))))

(def data-file-readers-ref
  (atom {"edn" read-edn
         "properties" read-properties}))

(defn- extension
  [s]
  (second (re-matches #".*\.([^.]*)" s )))

(def FILE_SIZE_LIMIT 10000000)

(defn data-file-reader
  [^File f]
  (and (.isFile f)
       ;; don't try to read giant data files, for now
       (< (.length f) FILE_SIZE_LIMIT) 
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

(try
 (let [yaml-read (requiring-resolve 'cognitect.rebl.impl.yaml/load-all)]
   (swap!
    data-file-readers-ref
    assoc
    "yml" yaml-read
    "yaml" yaml-read))
 (catch Throwable _))

(extend-protocol p/Datafiable
  java.io.File
  (datafy [f]
          (cond
           (.isFile f) (if-let [rf (data-file-reader f)]
                         (with-meta (rf f) (node-meta f))
                         (datafy-file f))
           (.isDirectory f) (datafy-directory f)
           (false? (.exists f)) (assoc (datafy-file f) :exists false) 
           :default f)))

(defn not-empty?
  "Returns true if x is a datafied file that is not empty"
  [x]
  (let [^File obj (-> x meta ::datafy/obj)]
    (and (instance? java.io.File obj)
         (.isFile obj)
         (< 0 (.length obj)))))

;; mimetable defined by "content.types.user.table" property
;; default built-in table at lib/content-types.properties
(defn browsable-filename?
  [fn]
  (when-let [type (.getContentTypeFor (java.net.URLConnection/getFileNameMap) fn)]
    (or (str/starts-with? type "text/")
        (str/starts-with? type "image/"))))

(defn browsable-file?
  [x]
  (let [m (meta x)
        ^File f (::datafy/obj m)]
    (-> (and (instance? File f)
             (:length x)
             (<= (:length x) FILE_SIZE_LIMIT) 
             (browsable-filename? (.getName f)))
        boolean)))

(def code-extensions-ref
  (atom #{"xml" "clj" "cljx" "cljs" "css" "js"
          "edn" "json" "java" "rb" "py"}))

(defn code-file?
  [x]
  (let [m (meta x)
        ^File f (::datafy/obj m)]
    (-> (and (instance? File f)
             (:length x)
             (<= (:length x) FILE_SIZE_LIMIT) 
             (get @code-extensions-ref (extension (.getName f))))
        boolean)))

