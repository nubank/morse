;; Copyright (c) Nu North America, Inc. All rights reserved.

(ns cognitect.rebl.impl.js
  (:require
    [cognitect.rebl.impl.js-object :as jso])
  (:import
    [javafx.scene.web WebEngine]
    [netscape.javascript JSObject]))

(set! *warn-on-reflection* true)

(defn ^JSObject callable
  "Returns a JSObject that can be used for calling global JS"
  [^WebEngine engine obj]
  (.executeScript engine (str "window." obj)))

(defn call
  "Calls js-function (keyword or string) w/args against js-callable
  if js-callable is a string, will be automatically converted to a callable JSObject.

  Returns the result of the js-function call."
  ^JSObject [^WebEngine engine js-callable js-function & args]
  (let [^JSObject js-object (if (string? js-callable)
                              (callable engine js-callable)
                              js-callable)
        js-function (jso/->js engine js-function)
        fn-obj (.call js-object js-function (object-array (map (partial jso/->js engine) args)))]
    fn-obj))
