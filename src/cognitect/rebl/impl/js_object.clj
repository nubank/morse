;;   Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.impl.js-object
  (:require
    [clojure.string :as str])
  (:import
    netscape.javascript.JSObject
    [javafx.scene.web WebEngine]
    [clojure.lang IFn Keyword]
    [java.util Map List]))

(set! *warn-on-reflection* true)

;; See https://www.oracle.com/technetwork/java/javase/documentation/liveconnect-docs-349790.html#JAVA_JS_CONVERSIONS
(defprotocol ToJs
  (->js* [x we]))

;; maybe need to box everything because JS doesn't look inside JSObject?
(extend-protocol ToJs
  nil
  (->js* [_ _]
    nil)

  Object
  (->js*
    [x _]
    x)

  Keyword
  (->js* [x _]
    (name x))

  List
  (->js*
    [s we]
    (let [elems (mapv #(->js* % we) s)]
      (let [result (.executeScript ^WebEngine we "new Array();")]
        (doseq [idx (range (count elems))]
          (.setSlot ^JSObject result idx (nth elems idx)))
        result)))

  Map
  (->js*
    [m we]
    (let [result (.executeScript ^WebEngine we "new Object();")]
      (doseq [[k v] m]
        (.setMember ^JSObject result (->js* k we) (->js* v we)))
      result))

  IFn
  (->js*
    [f ^WebEngine we]
    (let [^JSObject window (.executeScript we "window")
          wrap-clj-fn ^JSObject (.getMember window "wrapCljFn")]
      (.call wrap-clj-fn "call" (object-array [window f])))))

(defn ->js
  "To convert functions, WebEngine must have a global wrapCljFn function, which returns a function that calls invoke on f"
  [we x]
  (->js* x we))

(comment

  (in-ns 'cognitect.rebl.impl.js-object)
  (require '[cognitect.rebl.fx :as fx])

  (fx/later
    (fn []
      (try
        (def we (WebEngine.))
        (def js (->js we [{:fq/a 1 :b {:c 3}}]))
        (prn :js js)
        (catch Throwable t
          (.printStackTrace t)))))

  (in-ns 'user)
  (java.io.File. "../rebl/test/data")
  )