;;   Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl
  (:require 
   [clojure.main :as main]
   [clojure.core.async :as async :refer [>!! <!! chan mult]]
   [cognitect.rebl.config :as config]
   [cognitect.rebl.eval :as eval]))

(comment "an atom on map with keys:
:browsers and :viewers - :identk -> {:keys [pred ctor]}")
(defonce  registry (atom {:browsers {} :viewers {}}))

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
  choicek - subset of the registry choices that apply to val, with :id added
  :pref - the identk of preferred choice"
  [choicek prefk val]
  (let [reg @registry
        prefs @config/prefs
        cs (choicek reg)
        ps (prefk prefs)
        vs (reduce-kv (fn [ret k {:keys [pred] :as v}]
                        (if (and pred (pred val))
                          (assoc ret k (assoc v :id k))
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

(defn is-browser? [identk]
  (-> @registry :browsers (contains? identk)))

(defonce ^:private echan (chan 10))
(defonce ^:private exprs (mult echan))

(defn ui
  "Creates a new UI window"
  []
   (require 'cognitect.rebl.ui 'cognitect.rebl.charts 'cognitect.rebl.renderers)
    ;;init javafx w/o deriving app from anything
  (javafx.embed.swing.JFXPanel.)
  ((resolve 'cognitect.rebl.ui/create) {:exprs-mult exprs})
  nil)

(defn submit [expr val]
  (>!! echan {:event :rebl/editor-eval
              :form (pr-str expr)
              :val (if (instance? Throwable val) (Throwable->map val) val)}))

(defmacro inspect
  "sends the expr and its value to the REBL UI"
  [expr]
  `(submit '~expr ~expr))

(defn repl-read-string
  "main/repl-read sourdough"
  [request-prompt request-exit]
  (or ({:line-start request-prompt :stream-end request-exit}
        (main/skip-whitespace *in*))
      (let [input (eval/read-form-string *in*)]
        (main/skip-if-eol *in*)
        input)))

(def ^:private evaluator (atom :rebl))

(defn repl
  "starts a repl on stdio and launches a REBL UI connected to it"
  []
  (let [ch (async/chan)
        ev (fn [form]
             (let [evaluator @evaluator
                   eval (get (eval/evaluators) evaluator)]
               (eval {:form form :bindings (get-thread-bindings) :ret-fn #(async/put! ch %1)
                      :source "REPL" :evaluator evaluator})
               (when-let [{:keys [val ex bindings] :as ret} (<!! ch)]
                 (>!! echan ret )
                 (if ex
                   (throw ex)
                   (do
                     (doseq [[^clojure.lang.Var v b] bindings]
                       (when (.getThreadBinding v)
                         (.set v b)))
                     val)))))]
    (ui)
    (main/repl :init (fn []
                       (in-ns 'user)
                       (apply require main/repl-requires))
               :read repl-read-string
               :eval ev)))

#_(defn old-repl
  "starts a repl on stdio and launches a REBL UI connected to it"
  []
  (let [ev (fn [form]
             (let [expr (read-string form)
                   ret (try (eval expr)
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
               :read repl-read-string
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
