;;   Copyright (c) Nu North America, Inc. All rights reserved.

(ns dev.nu.morse.config
  (:import [java.io IOException])
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(def ^:private config-dir (io/file ".rebl"))
(def ^:private prefs-file (io/file config-dir "prefs.edn"))
(def ^:private io-failed-ref (atom false))

(defn- ensure-config-dir
  []
  (.mkdirs config-dir)
  true)

(defn write-prefs
  [r]
  (when-not @io-failed-ref
    (try
     (ensure-config-dir)
     (binding [*print-length* nil
               *print-level* nil]
       (spit prefs-file r))
     (.getCanonicalPath prefs-file)
     (catch IOException e
       (reset! io-failed-ref true)
       (.printStackTrace e)))))

(defn- read-prefs
  []
  (when-not @io-failed-ref
    (try
     (when (.exists prefs-file)
       (-> prefs-file slurp edn/read-string))
     (catch IOException e
       (reset! io-failed-ref true)
       (.printStackTrace e)
       nil))))

;; :browser-prefs and viewer-prefs - #{:identks...} -> :preferred-identk
(def prefs
  (atom (or (read-prefs)
            {:browser-prefs {}
             :viewer-prefs {}})))

(defn update-browser-prefs
  [identset pref]
  (doto (swap! prefs update :browser-prefs assoc identset pref)
    write-prefs)
  nil)

(defn update-viewer-prefs
  [identset pref]
  (doto (swap! prefs update :viewer-prefs assoc identset pref)
    write-prefs)
  nil)


