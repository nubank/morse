;;   Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.ui
  (:require
   [cognitect.rebl :as rebl]
   [cognitect.rebl.fx :as fx]
   [cognitect.rebl.renderers :as rend]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.core.async :as async :refer [<!! chan tap untap]])
  (:import [javafx.fxml FXMLLoader]
           [javafx.scene Scene]
           [javafx.event EventHandler]
           [javafx.application Platform]
           [javafx.scene.input KeyEvent KeyCodeCombination KeyCode KeyCombination$Modifier]
           [javafx.scene.control Tooltip]))

(defn clear-deck [{:keys [state] :as ui}]
  (swap! state dissoc :on-deck))

(swap! rebl/registry update-in [:viewers]
       assoc
       :rebl/edn {:pred #'any? :ctor #'rend/plain-edn-viewer}
       :rebl/spec-edn {:pred #'s/spec? :ctor #'rend/spec-edn-viewer}
       :rebl/map {:pred #'fx/Map? :ctor #'rend/map-vb}
       :rebl/coll {:pred #'fx/Coll? :ctor #'rend/coll-vb}
       :rebl/tuples {:pred #'fx/tuples? :ctor #'rend/tuples-vb}
       :rebl/maps {:pred #'fx/uniformish-maps? :ctor #'rend/maps-vb}
       :rebl/map-of-maps {:pred #'fx/uniformish-map-of-maps? :ctor #'rend/map-of-maps-vb}
       :rebl/throwable-map {:ctor #'rend/throwable-map-vb :pred #'fx/throwable-map?}
       :rebl/throwable {:ctor #'rend/throwable-vb :pred #'fx/throwable?}
       :rebl/var {:ctor #'rend/var-vb :pred #'var?}
       :rebl/ns-publics {:ctor #'rend/ns-publics-vb :pred #'fx/namespace?}
       :rebl/atom {:ctor #'rend/atom-vb :pred #'fx/atom?})

(swap! rebl/registry update-in [:browsers]
       assoc
       :rebl/map {:pred #'fx/Map? :ctor #'rend/map-vb}
       :rebl/var {:ctor #'rend/var-vb :pred #'var?}
       :rebl/coll {:pred #'fx/Coll? :ctor #'rend/coll-vb}
       :rebl/tuples {:pred #'fx/tuples? :ctor #'rend/tuples-vb}
       :rebl/maps {:pred #'fx/uniformish-maps? :ctor #'rend/maps-vb}
       :rebl/map-of-maps {:pred #'fx/uniformish-map-of-maps? :ctor #'rend/map-of-maps-vb}
       :rebl/ns-publics {:ctor #'rend/ns-publics-vb :pred #'fx/namespace?}
       :rebl/atom {:ctor #'rend/atom-vb :pred #'fx/atom?})

(declare val-selected)

(defn viewer-for
  "returns {:keys [view-ui view-options view-choice]}"
  [ui val]
  (let [{:keys [viewers pref]} (rebl/viewers-for val)
        p (pref viewers)
        vw (if (rebl/is-browser? pref)
                ((:ctor p) val (partial val-selected ui))
                ((:ctor p) val))]
    {:view-ui vw
     ;;incorporate :id for choice control
     :view-options (-> viewers vals vec)
     :view-choice p}))

(defn update-choice [control options choice]
  (-> control (.setItems (fx/fxlist options)))
  (-> control (.setValue choice)))

(defn update-pane [pane ui]
  (-> pane .getChildren (.setAll [ui])))

(defn view [{:keys [state view-pane viewer-choice fwd-button] :as ui} path-seg val]
  (let [viewer (viewer-for ui val)]
    (clear-deck ui)
    (swap! state merge (assoc viewer :path-seg path-seg :view-val val))
    (update-choice viewer-choice (:view-options viewer) (:view-choice viewer))
    (update-pane view-pane (:view-ui viewer))
    (.setDisable fwd-button (-> val rebl/browsers-for :browsers empty?))))

(defn val-selected
  [{:keys [view-pane state] :as ui} node path-seg val]
  (fx/later #(if (identical? (fx/current-ui view-pane) node)
               (swap! state assoc :on-deck {:path-seg path-seg :val val})
               (view ui path-seg val))))

(defn viewer-chosen [{:keys [state view-pane] :as ui} choices choice]
  (let [{:keys [view-choice view-val]} @state]
    (when (and choice (not= view-choice choice))
      (rebl/update-viewer-prefs (into #{} (map :id) choices) (:id choice))
      (clear-deck ui)
      (let [vw (if (rebl/is-browser? (:id choice))
                 ((:ctor choice) view-val (partial val-selected ui))
                 ((:ctor choice) view-val))]
        (swap! state assoc :view-choice choice :view-ui vw)
        (update-pane view-pane vw)))))

(defn browser-for
  "returns {:keys [browse-ui browse-options browse-choice]}"
  [ui val]
  (let [{:keys [browsers pref]} (rebl/browsers-for val)]
    {:browse-ui ((-> browsers pref :ctor) val (partial val-selected ui))
     ;;incorporate :id for choice control
     :browse-options (-> browsers vals vec)
     :browse-choice (browsers pref)}))

(defn browse-with [{:keys [state browse-pane browser-choice] :as ui} browser val]
  #_(clear-deck ui)
  (swap! state merge (assoc browser :browse-val val))
  (update-choice browser-choice (:browse-options browser) (:browse-choice browser))
  (update-pane browse-pane (:browse-ui browser))
  (.requestFocus (:browse-ui browser)))

(defn browse [ui val]
  (browse-with ui (browser-for ui val) val))

(defn user-vars
  []
  (into
   (sorted-map-by (fn [a b] (compare (name a) (name b))))
   (map (fn [[s v]] [s @v]))
   (ns-publics 'user)))

(defn browse-user-namespace
  [{:keys [exprs]}]
  (async/put! exprs {:eval '(cognitect.rebl.ui/user-vars)}))

(defn browser-chosen [{:keys [state browse-pane] :as ui} choices choice]
  (let [{:keys [browse-choice browse-val]} @state]
    (when (and choice (not= browse-choice choice))
      (rebl/update-browser-prefs (into #{} (map :id) choices) (:id choice))
      (clear-deck ui)
      (let [br ((:ctor choice) browse-val (partial val-selected ui))]
        (swap! state assoc :browse-choice choice :browse-ui br)
        (update-pane browse-pane br)
        (.requestFocus br)))))

(defn rtz [{:keys [state state-history eval-table eval-history
                   browse-pane browser-choice code-view root-button back-button] :as ui}]
  (reset! state-history ())
  (let [ehist @eval-history
        val (-> ehist first :val)
        bc {:id :rebl/eval-history}
        browser {:browse-ui eval-table
                 :browse-options [bc]
                 :browse-choice bc}]
    (clear-deck ui)
    (swap! state merge browser)
    (update-choice browser-choice [bc] bc)
    (update-pane browse-pane eval-table)
    (-> eval-table .getSelectionModel .clearSelection)
    (-> eval-table .getItems (.setAll ehist))
    ;;(set-code code-view (-> ehist first :expr))
    (.setDisable root-button true)
    (.setDisable back-button true)
    (-> eval-table .getSelectionModel .selectFirst)
    (.requestFocus eval-table)))

(defn load-expr [{:keys [eval-history code-view] :as ui} n]
  (if (= -1 n)
    (fx/reset-code code-view)
    (fx/set-code code-view (-> @eval-history (nth n) :expr))))

(defn next-expr [{:keys [expr-ord] :as ui}]
  (let [n (swap! expr-ord #(if (< -1 %1 ) (dec %1) %1))]
    (load-expr ui n)))

(defn prev-expr [{:keys [expr-ord eval-history] :as ui}]
  (let [n (swap! expr-ord #(if (< %1 (-> eval-history deref count dec)) (inc %1) %1))]
    (load-expr ui n)))

(defn expr-loop [{:keys [exprs eval-history follow-editor-check] :as ui}]
  (binding [*file* "user/rebl.clj"]
    (in-ns 'user)
    (apply require clojure.main/repl-requires)
    (loop []
      (let [msg (<!! exprs)]
        (when msg
          (let [eval? (contains? msg :eval)
                msg (if eval?
                      {:expr (:eval msg)
                       :val (try (eval (:eval msg))
                                 (catch Throwable ex
                                   (Throwable->map ex)))}
                      msg)]
            (when (or eval? (.isSelected follow-editor-check))
              (swap! eval-history conj msg)
              (fx/later #(rtz ui)))
            (recur)))))))

(defonce ^:private ui-count (atom 0))

(defn eval-pressed [{:keys [code-view exprs expr-ord] :as ui}]
  ;;TODO handle bad form
  (reset! expr-ord -1)
  (let [code (fx/get-code code-view)
        form (read-string code)]
    (fx/reset-code code-view)
    (async/put! exprs {:eval form})))

(defn fwd-pressed [{:keys [state state-history root-button back-button] :as ui}]
  (let [{:keys [view-val view-ui view-choice on-deck] :as statev} @state]
    (swap! state-history conj (dissoc statev :on-deck))
    (if (rebl/is-browser? (:id view-choice))
      (let [{:keys [browsers]} (rebl/browsers-for val)]
        (browse-with ui
                     {:browse-options (-> browsers vals vec)
                      :browse-ui view-ui
                      :browse-choice (browsers view-choice)}
                     view-val)
        (when-let [{:keys [path-seg val]} on-deck]
          (view ui path-seg val)))
      (browse ui view-val))
    (.setDisable root-button false)
    (.setDisable back-button false)))

(defn back-pressed [{:keys [state state-history root-button back-button fwd-button eval-button
                            code-view
                            browse-pane view-pane browser-choice viewer-choice] :as ui}]
  (let [{:keys [browse-ui path-seg view-val]} @state
        [[ostate] nhist] (swap-vals! state-history pop)
        ostate (cond-> ostate (identical? browse-ui (:view-ui ostate))
                       (assoc :on-deck {:path-seg path-seg :val view-val}))]
    (reset! state ostate)
    (update-pane browse-pane (:browse-ui ostate))
    (update-pane view-pane (:view-ui ostate))
    (update-choice browser-choice (:browse-options ostate) (:browse-choice ostate))
    (update-choice viewer-choice (:view-options ostate) (:view-choice ostate))
    (.setDisable root-button (empty? nhist))
    (.setDisable back-button (empty? nhist))
    (.setDisable fwd-button (-> (:view-val ostate) rebl/browsers-for :browsers empty?))
    (.requestFocus (:browse-ui ostate))))

(defn- get-optional
  [^java.util.Optional o]
  (when (.isPresent o) (.get o)))

(defn def-in-ns
  [ns sym v doc]
  (binding [*ns* (find-ns ns)]
    (eval `(def ~sym ~doc (quote ~v)))))

(defn def-as
  [{:keys [exprs state]}]
  (let [v (:view-val @state)
        dlg (doto (javafx.scene.control.TextInputDialog. "foo")
              (.setTitle "def as")
              (.setHeaderText "define a var")
              (.setContentText "name"))]
    (when-let [name (-> dlg .showAndWait get-optional)]
      (def-in-ns 'user (symbol name) v "Defined by cognitect.rebl def as...")
      ;; another option -- add the var itself to the UI history?
      #_(async/put! exprs {:eval (find-var (symbol "user" name))}))))

(def e->et
  {:pressed  KeyEvent/KEY_PRESSED
   :released KeyEvent/KEY_RELEASED})

(defn wire-handlers [{:keys [root-button back-button fwd-button eval-button def-button
                             viewer-choice browser-choice
                             scene eval-table code-view browse-pane view-pane] :as ui}]
  (let [wire-button (fn [f b]
                      (.setOnAction b (reify EventHandler (handle [_ e] (f)))))
        wire-key (fn wire-key
                   [f e k & cs]
                   (.addEventFilter scene (e->et e)
                     (let [kc (KeyCodeCombination. k (into-array KeyCombination$Modifier cs))]
                       (reify EventHandler
                         (handle [_ e]
                           (when (.match kc e)
                             (.consume e)
                             (f)))))))
        ttfont (javafx.scene.text.Font. 14.0)
        tooltip (fn [node text] (Tooltip/install node (doto (Tooltip. text)
                                                        (.setFont ttfont)
                                                        (.setWrapText true))))]
    ;;keys
    (wire-key #(eval-pressed ui) :pressed KeyCode/ENTER KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(fx/reset-code (:code-view ui)) :released KeyCode/ENTER KeyCodeCombination/CONTROL_DOWN)
    ;;sending focus to parent pane doesn't work
    (wire-key #(-> browse-pane fx/current-ui .requestFocus) :pressed KeyCode/B KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(.requestFocus browser-choice) :pressed KeyCode/B KeyCodeCombination/CONTROL_DOWN KeyCodeCombination/SHIFT_DOWN)
    (wire-key #(-> view-pane fx/current-ui .requestFocus) :pressed KeyCode/V KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(.requestFocus viewer-choice) :pressed KeyCode/V KeyCodeCombination/CONTROL_DOWN KeyCodeCombination/SHIFT_DOWN)
    (wire-key #(.requestFocus code-view) :pressed KeyCode/R KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(when-not (.isDisabled fwd-button) (fwd-pressed ui)) :pressed KeyCode/RIGHT KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(when-not (.isDisabled back-button) (back-pressed ui)) :pressed KeyCode/LEFT KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(when-not (.isDisabled root-button) (rtz ui)) :pressed KeyCode/LEFT
              KeyCodeCombination/CONTROL_DOWN KeyCodeCombination/SHIFT_DOWN)
    (wire-key #(prev-expr ui) :pressed KeyCode/UP KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(next-expr ui) :pressed KeyCode/DOWN KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(browse-user-namespace ui) :pressed KeyCode/U KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(def-as ui) :pressed KeyCode/D KeyCodeCombination/CONTROL_DOWN)
    ;;buttons
    (wire-button #(eval-pressed ui) eval-button)
    (wire-button #(fwd-pressed ui) fwd-button)
    (wire-button #(back-pressed ui) back-button)
    (wire-button #(rtz ui) root-button)
    (wire-button #(def-as ui) def-button)
    ;;choice controls
    (-> viewer-choice .valueProperty (.addListener (fx/change-listener (fn [ob ov nv]
                                                                         (viewer-chosen ui (.getItems viewer-choice) nv)))))
    (-> browser-choice .valueProperty (.addListener (fx/change-listener (fn [ob ov nv]
                                                                          (browser-chosen ui (.getItems browser-choice) nv)))))
    ;;tooltips
    (tooltip root-button "Nav to root (eval history) ^⇧LEFT")
    (tooltip back-button "Nav back ^LEFT")
    (tooltip fwd-button "Nav forward (browse the currently viewed value) ^RIGHT")
    (tooltip eval-button "eval code (in editor above) ^ENTER")
    (tooltip code-view "edit code for evaluation here, with parinfer support. eval with ^ENTER, load prev/next exprs with ^UP/^DOWN")
    (tooltip eval-table "browser pane, focus with ^B")
    (tooltip browser-choice "choose browser UI, focus with ^⇧B")
    (tooltip viewer-choice "choose viewer UI, focus with ^⇧V")
    (tooltip view-pane "viewer pane, focus with ^V")
    
    ;;this handling is special and not like other browsers
    (fx/add-selection-listener eval-table (fn [idx row]
                                            (let [{:keys [expr val]} row]
                                              ;;(set-code code-view expr)
                                              (view ui idx val))))))

(defn- init [{:keys [exprs-mult]}]
  (fx/later
   #(try (let [loader (FXMLLoader. (io/resource "rebl.fxml"))
               root (.load loader)               
               names (.getNamespace loader)
               node (fn [id] (.get names id))
               scene (Scene. root 1200 800)
               exprs (chan 10)
               stage (javafx.stage.Stage.)
               _ (.setScene stage scene)
               
               vc (proxy [javafx.util.StringConverter] []
                    (toString [v] (-> v :id str)))
               ui {:scene scene
                   :stage stage
                   :exprs exprs
                   :state (atom {:browse-choice {:id :rebl/eval-history}})
                   :expr-ord (atom -1)
                   :state-history (atom ())
                   :eval-history (atom ())
                   :eval-table (doto (node "evalTable")
                                 (.setItems (fx/fxlist (java.util.ArrayList.))))
                   :expr-column (doto (node "exprColumn")
                                  (.setCellValueFactory (fx/cell-value-callback (comp fx/finite-pr-str :expr))))
                   :val-column (doto (node "valColumn")
                                 (.setCellValueFactory (fx/cell-value-callback (comp fx/finite-pr-str :val))))
                   :code-view (doto (node "codeView")
                                #_(.setZoom 1.2))
                   :follow-editor-check (node "followEditorCheck")
                   :eval-button (node "evalButton")
                   :browser-choice (doto (node "browserChoice")
                                     (.setConverter vc))
                   :browse-pane (node "browsePane")
                   :def-button (node "defButton")
                   :viewer-choice (doto (node "viewerChoice")
                                    (.setConverter vc))
                   :view-pane (node "viewPane")
                   :root-button (doto (node "rootButton")
                                  (.setDisable true))
                   :back-button (doto (node "backButton")
                                  (.setDisable true))
                   :fwd-button (doto (node "fwdButton")
                                 (.setDisable true))}]
           (-> scene .getStylesheets (.add (str (io/resource "fx.css"))))
           (.setTitle stage (str "REBL " (swap! ui-count inc)))
           (.show stage)
           (-> (:code-view ui) .getEngine (.load (str (io/resource "codeview.html"))))
           (wire-handlers ui)
           (tap exprs-mult exprs)
           (.setOnHidden stage (reify EventHandler (handle [_ _]
                                                     (untap exprs-mult exprs)
                                                     (async/close! exprs)))) 
           (async/thread (clojure.main/with-bindings (expr-loop ui))))
         (catch Throwable ex
           (println ex)))))

(defn create [argmap]
  (Platform/setImplicitExit false)
  (init argmap)
  nil)

