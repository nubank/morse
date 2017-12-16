;;   Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl
  (:require 
   [clojure.main :as main]
   [clojure.core.async :as async :refer [>!! <!! chan mult]]))

#_(defn- eval-ch [exprs rets]
  (binding [*file* "user/rebl.clj"]
    (in-ns 'user)
    (apply require main/repl-requires)
    (require '[cognitect.rebl :as rebl])
    (loop []
      (let [{:keys [source expr]} (<!! exprs)
            ret (try (eval expr)
                     (catch Throwable ex
                       ex))]
        (swap! history conj {:expr expr :val ret})
        (when (= source :repl)
          (>!! rets [ret]))
        (recur)))))

(comment "an atom on map with keys:
:browsers and :viewers - :identk -> {:keys [pred ctor]}
:browser-prefs and viewer-prefs - #{:identks...} -> :preferred-identk")
(defonce  registry
  ;;TODO - durable prefs
  ;;TODO - API for handler registration?
  (atom {:browsers {}
         :viewers {}
         :browser-prefs {#{:rebl/coll :rebl/tuples} :rebl/tuples
                         #{:rebl/maps :rebl/tuples} :rebl/tuples
                         #{:rebl/maps :rebl/coll} :rebl/maps}
         :viewer-prefs {#{:rebl/edn :rebl/coll} :rebl/coll
                        #{:rebl/edn :rebl/spec-edn} :rebl/spec-edn
                        #{:rebl/edn :rebl/coll :rebl/tuples} :rebl/tuples
                        #{:rebl/edn :rebl/map} :rebl/map
                        #{:rebl/edn :rebl/coll :rebl/maps} :rebl/maps
                        #{:rebl/edn :rebl/map :rebl/exception} :rebl/exception
                        #{:rebl/edn :rebl/var} :rebl/var
                        #{:rebl/ns-publics :rebl/edn} :rebl/ns-publics}}))

(defn update-browsers
  "Update the available browsers. browsers is a map of

    :identk -> {:keys [pred ctor]}

See https://github.com/cognitect-labs/rebl/wiki/Extending-REBL."
  [browsers]
  (swap! registry update :browsers merge browsers)
  nil)

(defn update-viewers
  "Update the available browsers. browsers is a map of

    :identk -> {:keys [pred ctor]}

See https://github.com/cognitect-labs/rebl/wiki/Extending-REBL."
  [viewers]
  (swap! registry update :viewers merge viewers)
  nil)

(defn- choices-for
  "returns map with:
  choicek - subset of the registry choices that apply to val
  :pref - the identk of preferred choice"
  [choicek prefk val]
  (let [reg @registry
        cs (choicek reg)
        ps (prefk reg)
        vs (reduce-kv (fn [ret k {:keys [pred] :as v}]
                        (if (and pred (pred val))
                          (assoc ret k v)
                          ret))
                      {} cs)
        pref (or (ps (set (keys vs)))
                 (first (keys vs)))]
    {choicek vs
     :pref pref}))

(defn viewers-for
  "returns map with:
  :viewers - subset of the registry viewers that apply to val
  :pref - the identk of preferred viewer"
  [val]
  (choices-for :viewers :viewer-prefs val))

(defn browsers-for
  "returns map with:
  :browsers - subset of the registry browsers that apply to val
  :pref - the identk of preferred browser"
  [val]
  (choices-for :browsers :browser-prefs val))

(defonce ^:private echan (chan 10))
(defonce ^:private exprs (mult echan))

(defn ui
  "Creates a new UI window"
  []
   (require 'cognitect.rebl.ui)
    ;;init javafx w/o deriving app from anything
  (javafx.embed.swing.JFXPanel.)
  ((resolve 'cognitect.rebl.ui/create) {:exprs-mult exprs}))

(defn submit [expr val]
  (>!! echan {:event :rebl/editor-eval
              :expr expr
              :val (if (instance? Throwable val) (Throwable->map val) val)}))

(defmacro inspect
  "sends the expr and its value to the REBL UI"
  [expr]
  `(submit '~expr ~expr))

(defn repl
  "starts a repl on stdio and launches a REBL UI connected to it"
  []
  (let [ev (fn [expr]
             (let [ret (try (eval expr)
                            (catch Throwable ex
                              ex))]
               (submit expr ret)
               (if (instance? Throwable ret)
                 (throw ret)
                 ret)))]
    (ui)
    (main/repl :init (fn []
                       (in-ns 'user)
                       (apply require main/repl-requires))
               ;;TODO - we'd like to have the strings as well as the forms
               :eval ev)))

(defn -main []
  (repl))

(comment
;;
(javafx.embed.swing.JFXPanel.)
;;(identity javafx.application.Platform)
;;(identity javafx.scene.control.TextArea)
;;(FXMLLoader/load (io/resource "rebl.fxml"))
(Platform/setImplicitExit false)
(Platform/runLater #(try (let [root (FXMLLoader/load (io/resource "rebl.fxml"))
                               scene (Scene. root 1200 800)
                               stage (javafx.stage.Stage.)]
                           (.setScene stage scene)
                           (.show stage))
                         (catch Throwable ex
                           (println ex))))
)
