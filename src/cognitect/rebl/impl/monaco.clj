;; Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.impl.monaco
  (:require
    [cljfmt.core :as cljfmt]
    [cognitect.rebl.fx :as fx]
    [cognitect.rebl.impl.js :as js]
    [cognitect.rebl.impl.js-object :as jso])
  (:import
    [javafx.scene.web WebEngine WebView]
    [javafx.beans.value ChangeListener]
    [javafx.concurrent Worker$State]
    [netscape.javascript JSObject]))

(set! *warn-on-reflection* true)

(def models
  "Map describing the monaco models, for marshaling
  Key is the name of the model, for lookup
  Value is a map w/key of :properties or :methods, the type of the javascript target
  Value of that map is the set of properties/methods to call

  Note: Doesn't support methods that takes params"
  {:position {:properties #{:column :lineNumber}}

   :range {:properties #{:endColumn :endLineNumber :startColumn :startLineNumber}}

   :text-model {:methods #{:getFullModelRange :getValue}}})

(defn- ->map
  "model is the lookup in the models map.
  From the keys in models, retrieves values and returns map"
  [engine model ^JSObject js]
  (let [properties (reduce
                     (fn [m k]
                       (assoc m k (.getMember js (name k))))
                     {}
                     (get-in models [model :properties]))]
    (reduce
      (fn [m k]
        (assoc m k (js/call engine js k)))
      properties
      (get-in models [model :methods]))))

(defmulti js->clj "Convert JS object to Clojure" (fn [x _ _] x))

(defmethod js->clj :text-model
  [model engine js]
  (let [{full-model-range :getFullModelRange text :getValue} (->map engine model js)
        range (->map engine :range full-model-range)]
    {:fullModelRange full-model-range
     :range range
     :text text}))

(defn provide-on-type-formatting-edits
  "returns function for callback for OnTypeFormattingEditProvider"
  [^WebEngine engine {:keys [cljfmt-options]}]
  (fn [^JSObject text-model ^JSObject position ch ^JSObject options ^JSObject token]
    (try
      (let [{:keys [range text]} (js->clj :text-model engine text-model)
            formatted (cljfmt/reformat-string text cljfmt-options)]
        (jso/->js engine [{:range range
                           :text formatted}]))
      (catch Throwable t
        (.printStackTrace t)))))

(def provide-on-type-formatting-edits-fn
  "Memoized, to hold on to function that is being marshaled to JS"
  (memoize provide-on-type-formatting-edits))

(defn register-callbacks
  "Registers the language providers"
  [^WebEngine engine options]
  (js/call engine "monaco.languages"
           :registerOnTypeFormattingEditProvider
           :clojure {:autoFormatTriggerCharacters ["\n" "\r"]
                     :provideOnTypeFormattingEdits
                     (provide-on-type-formatting-edits-fn engine options)}))

(defn register-callback-listener
  "Returns listener that registers callbacks on success."
  ^ChangeListener [^WebEngine engine options]
  (fn [ob ov nv]
    (when (= nv Worker$State/SUCCEEDED)
      (try
        (register-callbacks engine options)
        (catch Throwable t
          (.printStackTrace t)
          nil)))))

(defn register
  [^WebView codeview options]
  (let [^WebEngine engine (.getEngine codeview)]
    (-> engine .getLoadWorker .stateProperty (.addListener ^ChangeListener (fx/change-listener (register-callback-listener engine options))))))

(comment
  (do
    (require
      '[cognitect.rebl.fx :as fx]
      '[clojure.java.io :as io]
      '[clojure.core.server :as server])
    (import
      '[com.sun.javafx.webkit WebConsoleListener])
    (def r (cognitect.rebl/repl server/prepl)))

  (fx/later
    (fn []
      (try
        (def engine (WebEngine.))
        (WebConsoleListener/setDefaultListener (reify WebConsoleListener
                                                 (messageAdded [this wv message line-number source-id]
                                                   (clojure.pprint/pprint {:message message
                                                                           :line-number line-number
                                                                           :source-id source-id}))))

        (.load engine (str (io/resource "cognitect/rebl/codeview.html")))
        (-> engine
            .getLoadWorker
            .stateProperty
            (.addListener ^ChangeListener
                          (fx/change-listener
                            (fn [ob ov nv]
                              (let [r (js/call engine (js/callable engine "monaco.languages") :getEncodedLanguageId :clojure)]
                                (prn :js-call r))
                              (let [r (js/call engine "monaco.languages" :getEncodedLanguageId :clojure)]
                                (prn :js-call r))))))
        (catch Throwable t
          (.printStackTrace t))))))