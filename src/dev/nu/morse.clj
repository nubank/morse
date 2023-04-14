;;   Copyright (c) Nu North America, Inc. All rights reserved.

(ns dev.nu.morse
  (:require 
   [clojure.main :as main]
   [clojure.edn :as edn]
   [clojure.core.server :as server]
   [clojure.pprint :as pp]
   [clojure.core.async :as async :refer [>!! <!! chan mult]]
   [cognitect.rebl.config :as config])
  (:refer-clojure :exclude [eval load-file])
  (:gen-class))

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
    ;(.println System/out (str choicek " " prefk " " (class val) ": " (pr-str {choicek vs :pref pref})))
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

(defonce ^:private echan (chan 1024))
(defonce ^:private exprs (mult echan))

(defn ui
  "Creates a new UI window"
  [& {:keys [proc mode] :or {proc server/prepl}}]
   (require 'cognitect.rebl.ui 'cognitect.rebl.charts 'cognitect.rebl.renderers)
    ;;init javafx w/o deriving app from anything
  (javafx.embed.swing.JFXPanel.)
  ((resolve 'cognitect.rebl.ui/create) {:exprs-mult exprs :proc proc :mode mode})
  nil)

(defn submit [expr val & {:keys [event tag] :or {event :rebl/editor-eval, tag :ret}}]
  (>!! echan {:event event
              :tag tag
              :form (pr-str expr)
              :val val}))

(defmacro inspect
  "sends the expr and its value to the REBL UI"
  [expr]
  `(let [ret# ~expr]
     (submit '~expr ret#)
     ret#))

(defn eval
  "Sends the expr to REBL for evaluation. Returns if the send was successful."
  [expr]
  (submit expr nil :tag :cognitect.rebl.ui/eval))

(defn load-file
  "Takes a filename and attempts to send each Clojure form found in it
  to REBL for evaluation."
  [filename]
  (with-open [r (clojure.lang.LineNumberingPushbackReader. (clojure.java.io/reader filename))]
    (binding [*read-eval* false]
      (let [forms (loop [acc [] rdr r]
                    (if-let [form (read {:eof nil} rdr)]
                      (recur (conj acc form) rdr)
                      acc))]
        (doseq [form forms]
          (eval form))))))

(defn repl [proc]
  (apply require main/repl-requires)
  (println "Clojure" (clojure-version))
  (printf "%s=> " (ns-name *ns*))
  (flush)
  (let [ch (async/chan)
        o *out*
        ex? (every-pred :cause :via)
        cb (fn [{:keys [tag val form ms ns] :as m}]
             (when-not (= tag :tap)
               (>!! echan (assoc m :rebl/source "REPL")))
             (binding [*out* o]
               (case tag
                     :err (do (print val) (flush))
                     :out (do (print val) (flush))
                     :tap nil
                     :ret
                     (do
                       (if (:exception m)
                         (binding [*out* *err*]
                           (print (-> val main/ex-triage main/ex-str))
                           (flush))
                         (prn val))
                       (printf "%s=> " ns)
                       (flush)))))]
    (ui :proc proc)
    (proc *in* cb)))

(defn morse
  ([{:keys [host port mode]
     :or {host "localhost", port 5555, mode :remote}}]
   (ui :proc (partial server/remote-prepl host port)
       :mode mode)))

(defn -main
  [& args]
  (let [{:keys [host port]
         :or {host "localhost", port 5555}
         :as opts} (and (seq args) (edn/read-string (first args)))
         mode :remote]
    (println "Connectiong Morse to remote at" (str host ":" port))
    (morse {:host host, :port port, :mode mode})))

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

(-main)
)
