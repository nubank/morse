;;   Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.ui
  (:require
   [cognitect.rebl :as rebl]
   [cognitect.rebl.config :as config]
   [cognitect.rebl.impl.monaco :as monaco]
   [cognitect.rebl.renderers :as render]
   [cognitect.rebl.fx :as fx]
   [clojure.datafy :as datafy]
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [clojure.spec.alpha :as s]
   [clojure.main :as main]
   [clojure.core.async :as async :refer [<!! chan tap untap]]
   [data.replicant.client.protocols :as rds]
   [data.replicant.client.spi :as rds-client]
   [data.replicant.client.reader :as rds-reader])
  (:import [javafx.fxml FXMLLoader]
           [javafx.scene Scene]
           [javafx.collections FXCollections ObservableList]
           [javafx.event EventHandler]
           [javafx.application Platform]
           [javafx.scene.input KeyEvent KeyCodeCombination KeyCode KeyCombination$Modifier]
           [javafx.scene.control CheckBox ListCell ListView Tooltip]
           [javafx.scene.control.cell TextFieldListCell]
           [javafx.util Callback]
           [java.text DateFormat]
           [java.io Writer PipedReader PipedWriter]))

(defn clear-deck [{:keys [state] :as ui}]
  (swap! state dissoc :on-deck))

(declare val-selected)

(defn viewer-for
  "returns {:keys [view-ui view-options view-choice]}"
  [ui val]
  ;;(prn {:viewer-for val})
  (let [{:keys [viewers pref]} (rebl/viewers-for val)
        p (pref viewers)
        vw (if (rebl/is-browser? pref)
                ((:ctor p) val (partial val-selected ui val))
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

(defn update-meta [{:keys [meta-table] :as ui} meta-map]
  (render/set-table-map meta-table meta-map nil))

(defn update-path [{:keys [path-text state] :as ui}]
  (let [{:keys [path path-seg]} @state]
    (.setText path-text (pr-str (conj path path-seg)))))

(defn view [{:keys [state view-pane viewer-choice fwd-button path-text] :as ui} path-seg sel-val]
  ;;(prn {:view val})
  (let [{:keys [path path-nav]} @state
        val (path-nav sel-val)
        viewer (viewer-for ui val)
        meta-map (meta val)]
    (clear-deck ui)
    (swap! state merge (assoc viewer :path-seg path-seg :sel-val sel-val :view-val val :view-meta meta-map))
    (update-path ui)
    (update-choice viewer-choice (:view-options viewer) (:view-choice viewer))
    (update-pane view-pane (:view-ui viewer))
    (update-meta ui meta-map)
    (.setDisable fwd-button (-> val rebl/browsers-for :browsers empty?))))

(defn val-selected
  [{:keys [view-pane browse-pane state] :as ui} coll node path-seg val]
  ;;(prn {:val-selected val :node node :browse-ui (:browse-ui @state)})
  (let [val (try (->> val (datafy/nav coll path-seg) render/datafy)
                 (catch Throwable t t))]
    (fx/later #(if (identical? (fx/current-ui view-pane) node)
                 (swap! state assoc :on-deck {:path-seg path-seg :val val})
                 (when (identical? (fx/current-ui browse-pane) node)
                   (view ui path-seg val))))))

(defn viewer-chosen [{:keys [state view-pane] :as ui} choices choice]
  (let [{:keys [view-choice view-val]} @state]
    (when (and choice (not= view-choice choice))
      (config/update-viewer-prefs (into #{} (map :id) choices) (:id choice))
      (clear-deck ui)
      (let [vw (if (rebl/is-browser? (:id choice))
                 ((:ctor choice) view-val (partial val-selected ui view-val))
                 ((:ctor choice) view-val))]
        (swap! state assoc :view-choice choice :view-ui vw)
        (update-pane view-pane vw)))))

(defn browser-for
  "returns {:keys [browse-ui browse-options browse-choice]}"
  [ui val]
  (let [{:keys [browsers pref]} (rebl/browsers-for val)]
    {:browse-ui ((-> browsers pref :ctor) val (partial val-selected ui val))
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
   (map (fn [[s v]] [s (deref v)]))
   (ns-publics 'user)))

(defn do-eval [{:keys [exprs]} form-string]
  (async/put! exprs {:tag ::eval :form form-string}))

(defn browse-user-namespace
  [ui]
  (do-eval ui "(into (sorted-map-by (fn [a b] (compare (name a) (name b)))) (map (fn [[s v]] [s (deref v)])) (ns-publics 'user))"))

(defn browser-chosen [{:keys [state browse-pane] :as ui} choices choice]
  (let [{:keys [browse-choice browse-val]} @state]
    (when (and choice (not= browse-choice choice))
      (config/update-browser-prefs (into #{} (map :id) choices) (:id choice))
      (clear-deck ui)
      (let [br ((:ctor choice) browse-val (partial val-selected ui browse-val))]
        (swap! state assoc :browse-choice choice :browse-ui br)
        (update-pane browse-pane br)
        (.requestFocus br)))))

(defn rtz [{:keys [state state-history eval-table eval-history
                   nav-text browse-tab-pane
                   browse-pane browser-choice code-view root-button back-button] :as ui}]
  (reset! state-history ())
  (let [ehist @eval-history
        val (-> ehist first :val)
        bc {:id :rebl/eval-history}
        browser {:browse-ui eval-table
                 :browse-options [bc]
                 :browse-choice bc}]
    (clear-deck ui)
    (.setText nav-text "")
    (swap! state merge browser {:path-nav identity :path [] :nav-forms []})
    (update-choice browser-choice [bc] bc)
    (update-pane browse-pane eval-table)
    (-> eval-table .getSelectionModel .clearSelection)
    (-> eval-table .getItems (.setAll ehist))
    ;;(set-code code-view (-> ehist first :expr))
    (.setDisable root-button true)
    (.setDisable back-button true)
    (-> eval-table .getSelectionModel .selectFirst)
    (-> browse-tab-pane .getSelectionModel .selectFirst)
    (.requestFocus eval-table)))

(defn load-expr [{:keys [eval-history code-view] :as ui} n]
  (if (= -1 n)
    (fx/reset-code code-view)
    (fx/set-code code-view (-> @eval-history (nth n) :form))))

(defn next-expr [{:keys [expr-ord] :as ui}]
  (let [n (swap! expr-ord #(if (< -1 %1 ) (dec %1) %1))]
    (load-expr ui n)))

(defn prev-expr [{:keys [expr-ord eval-history] :as ui}]
  (let [n (swap! expr-ord #(if (< %1 (-> eval-history deref count dec)) (inc %1) %1))]
    (load-expr ui n)))

(deftype Tapped
  [v]
  clojure.lang.IDeref
  (deref [_] v)
  java.lang.Object
  (toString
   [_]
   (binding [*print-length* 50
             *print-level* 2]
     (-> v pr-str (fx/finite-str 100)))))

(defn append-tap
  [{:keys [taps ^ListView tap-list-view ^ObservableList tap-list]} val]
  (.add tap-list (->Tapped val))
  (swap! taps conj val)
  tap-list-view)

(defn expr-loop [{:keys [exprs ^Writer eval-writer eval-history follow-editor-check title
                         ns-label out-text] :as ui}]
  (let [pending (java.util.concurrent.LinkedBlockingQueue.)]
    (loop []
      (let [{:keys [tag val ^String form ns rebl/source] :as msg} (<!! exprs)]
        (when msg
          (try
            ;(println (str "\nexpr-loop: " tag " " form " " (count pending)))
            (case tag
              ::eval (do (.write eval-writer form) (.write eval-writer "\n") (.flush eval-writer))
              ::rds (do (.write eval-writer form) (.write eval-writer "\n") (.flush eval-writer)
                        (.add ^java.util.Queue pending (:cb msg)))
              :ret (if (pos? (.size ^java.util.Queue pending))
                     (let [cb (.remove ^java.util.Queue pending)]
                       (future (deliver cb msg)))

                     (when (or (= source title) (.isSelected follow-editor-check))
                       (do
                         (swap! eval-history conj msg)
                         (send render/deps-agent render/refresh-deps)
                         (fx/later #(do
                                      (.setText ns-label (str "ns: " ns))
                                      (rtz ui))))))
              ;;TODO out/err/tap
              (:out :err) (fx/later #(do (.appendText out-text val)
                                         (.end out-text)))

              :tap (fx/later #(append-tap ui val))

              nil)
              (catch Exception e
                (.printStackTrace e)))
          (recur))))))

(defonce ^:private ui-count (atom 0))

(defn eval-pressed [{:keys [code-view expr-ord] :as ui}]
  ;;TODO handle bad form
  (reset! expr-ord -1)
  (let [code (fx/get-code code-view)]
    (fx/reset-code code-view)
    (do-eval ui code)))

(defn fwd-pressed [{:keys [state state-history root-button back-button nav-text] :as ui}]
  (let [{:keys [view-val view-ui view-choice on-deck path path-seg nav-forms] :as statev} @state]
    (swap! state-history conj (dissoc statev :on-deck))
    (.setText nav-text "")
    (swap! state assoc :path-nav identity :path (-> path (conj path-seg) (into nav-forms)))
    (if (rebl/is-browser? (:id view-choice))
      (let [{:keys [browsers]} (rebl/browsers-for view-val)]
        (browse-with ui
                     {:browse-options (-> browsers vals vec)
                      :browse-ui view-ui
                      :browse-choice (browsers (:id view-choice))}
                     view-val)
        (when-let [{:keys [path-seg val]} on-deck]
          (view ui path-seg val)))
      (browse ui view-val))
    ;;(update-path ui)
    (.setDisable root-button false)
    (.setDisable back-button false)))

(defn back-pressed [{:keys [state state-history root-button back-button fwd-button eval-button
                            code-view nav-text
                            browse-pane view-pane browser-choice viewer-choice] :as ui}]
  (let [{:keys [browse-ui path-seg sel-val]} @state
        [[ostate] nhist] (swap-vals! state-history pop)
        ostate (cond-> ostate (identical? browse-ui (:view-ui ostate))
                       (assoc :on-deck {:path-seg path-seg :val sel-val}))]
    (reset! state ostate)
    (.setText nav-text (:nav-str ostate))
    (update-path ui)
    (update-pane browse-pane (:browse-ui ostate))
    (update-pane view-pane (:view-ui ostate))
    (update-meta ui (:view-meta ostate))
    (update-choice browser-choice (:browse-options ostate) (:browse-choice ostate))
    (update-choice viewer-choice (:view-options ostate) (:view-choice ostate))
    (.setDisable root-button (empty? nhist))
    (.setDisable back-button (empty? nhist))
    (.setDisable fwd-button (-> (:view-val ostate) rebl/browsers-for :browsers empty?))
    (.requestFocus (:browse-ui ostate))))

(defn tap-clear-pressed
  [{:keys [taps ^ObservableList tap-list]}]
  (.clear tap-list)
  (reset! taps []))

(defn tap-browse-pressed
  [{:keys [taps exprs] :as ui}]
  (async/put! exprs {:tag :ret :form ":rebl/taps" :val @taps}))

(defn- get-optional
  [^java.util.Optional o]
  (when (.isPresent o) (.get o)))

(defn def-in-ns
  [ui ns sym v doc]
  (intern ns sym v)
  (let [fqs (symbol (str ns) (str sym))]
    (do-eval ui (pr-str `(var ~fqs)))))

(defn- original-val
  [statev]
  (let [v (:view-val statev)
        m (:view-meta statev)]
    (or (:clojure.datafy/obj m) v)))


(defn def-as
  [{:keys [state def-text] :as ui}]
  (let [v (original-val @state)
        name (.getText def-text)]
    (def-in-ns ui 'user (symbol name) v "Defined by cognitect.rebl 'def as:'")
    ;; another option -- add the var itself to the UI history?
    #_(async/put! exprs {::eval (find-var (symbol "user" name))})))

(defn set-nav-path
  [{:keys [state nav-text] :as ui}]
  (let [{:keys [sel-val path-seg]} @state
        nav-str (.getText nav-text)
        nav-forms (read-string (str "[" nav-str "]"))
        path-nav (render/path-nav nav-forms)]
    (swap! state assoc :nav-str nav-str :path-nav path-nav :nav-forms nav-forms)
    (view ui path-seg sel-val)))

(def e->et
  {:pressed  KeyEvent/KEY_PRESSED
   :released KeyEvent/KEY_RELEASED})

(defn wire-handlers [{:keys [root-button back-button fwd-button eval-button def-text nav-text
                             viewer-choice browser-choice
                             scene eval-table code-view browse-pane view-pane
                             tap-list-view tap-list tap-clear tap-browse] :as ui}]
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
    (wire-key #(when-not (.isDisabled fwd-button) (fwd-pressed ui)) :pressed KeyCode/RIGHT KeyCodeCombination/SHORTCUT_DOWN)
    (wire-key #(when-not (.isDisabled back-button) (back-pressed ui)) :pressed KeyCode/LEFT KeyCodeCombination/SHORTCUT_DOWN)
    (wire-key #(when-not (.isDisabled root-button) (rtz ui)) :pressed KeyCode/LEFT
              KeyCodeCombination/SHORTCUT_DOWN KeyCodeCombination/SHIFT_DOWN)
    (wire-key #(prev-expr ui) :pressed KeyCode/UP KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(next-expr ui) :pressed KeyCode/DOWN KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(browse-user-namespace ui) :pressed KeyCode/U KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(.requestFocus def-text) :pressed KeyCode/D KeyCodeCombination/CONTROL_DOWN)
    ;;buttons
    (wire-button #(eval-pressed ui) eval-button)
    (wire-button #(fwd-pressed ui) fwd-button)
    (wire-button #(back-pressed ui) back-button)
    (wire-button #(rtz ui) root-button)
    ;;(wire-button #(def-as ui) def-button)
    (wire-button #(def-as ui) def-text)
    (wire-button #(set-nav-path ui) nav-text)
    (wire-button #(tap-clear-pressed ui) tap-clear)
    (wire-button #(tap-browse-pressed ui) tap-browse)
    
    ;;choice controls
    (-> viewer-choice .valueProperty (.addListener (fx/change-listener (fn [ob ov nv]
                                                                         (viewer-chosen ui (.getItems viewer-choice) nv)))))
    (-> browser-choice .valueProperty (.addListener (fx/change-listener (fn [ob ov nv]
                                                                          (browser-chosen ui (.getItems browser-choice) nv)))))

    #_(-> def-text .textProperty (.addListener (fx/change-listener (fn [ob ov nv]
                                                                    (.setDisable def-button (zero? (count nv)))))))
    ;; checkboxes


    ;;tooltips
    (tooltip root-button "Nav to root (eval history) ⌘⇧LEFT")
    (tooltip back-button "Nav back ⌘LEFT")
    (tooltip fwd-button "Nav forward (browse the currently viewed value) ⌘RIGHT")
    (tooltip eval-button "eval code (in editor above) ^ENTER")
    (tooltip code-view "edit code for evaluation here, with paredit support. eval with ^ENTER, load prev/next exprs with ^UP/^DOWN")
    (tooltip eval-table "browser pane, focus with ^B")
    (tooltip browser-choice "choose browser UI, focus with ^⇧B")
    (tooltip viewer-choice "choose viewer UI, focus with ^⇧V")
    (tooltip view-pane "viewer pane, focus with ^V")
    (tooltip nav-text "nav ahead by supplying a path of keys and/or *parenthesized* forms (treated as per ->).\nThis lets you stay on a collection while viewing nested/transformed elements.\nFocus from browse with SHIFT-TAB, return to browse with TAB")
    
    ;;this handling is special and not like other browsers
    (fx/add-selection-listener eval-table (fn [idx row]
                                            (let [{:keys [expr val]} row]
                                              ;;(set-code code-view expr)
                                              (view ui idx (render/datafy val)))))))

(def tap-cell-proxy (delay (eval '(fn [] (proxy [javafx.scene.control.cell.TextFieldListCell] [])))))

(defn tap-cell-factory
  []
  (reify
   Callback 
   (call
    [this val]
    (let [tooltip (Tooltip.)]
      (update-proxy (@tap-cell-proxy)
                    {"updateItem"
                     (fn [this item empty?]
                       (proxy-super updateItem item empty?)
                       (when-not empty?
                         (.setText tooltip (-> (fx/finite-pprint-str @item)
                                               (fx/ellipsize 2048)))
                         (.setTooltip this tooltip)))})))))

(defn- make-sequencing-event-fn [chan & {:keys [stringify-err stringify-form] :or {stringify-err pr-str, stringify-form pr-str}}]
  (fn [op]
    (let [p (promise)]
      (async/put! chan {:tag ::rds :form (stringify-form op) :cb p})
      (let [{:keys [val exception]} @p]
        (.println System/out (str "return of rds-call " (class val) :--> val))
        (if exception
          (do
            ;; TODO: Throwable map - what to do?
            (.println System/out (stringify-err val))
            nil)
          val)))))

(defn- build-ui [exprs-mult]
  (let [loader (FXMLLoader. (io/resource "cognitect/rebl/rebl.fxml"))
        root (.load loader)
        names (.getNamespace loader)
        node (fn [id] (.get names id))
        scene (Scene. root 1200 800)
        pwr (java.io.PipedWriter.)
        exprs (chan 100)
        stage (javafx.stage.Stage.)
        _ (.setScene stage scene)
        tf (DateFormat/getTimeInstance DateFormat/MEDIUM)

        vc (proxy [javafx.util.StringConverter] []
             (toString [v] (-> v :id str)))
        tap-list-view (node "tapList")
        tap-list (FXCollections/observableArrayList)
        ui {:title (str "REBL " (swap! ui-count inc))
            :scene scene
            :stage stage
            :exprs exprs
            :state (atom {:browse-choice {:id :rebl/eval-history}
                          :path-nav identity
                          :nav-forms []
                          :path []})
            :expr-ord (atom -1)
            :state-history (atom ())
            :eval-history (atom ())
            :taps (atom [])
            :eval-writer pwr
            :eval-table (doto (node "evalTable")
                          (.setItems (fx/fxlist (java.util.ArrayList.))))
            :meta-table (node "metaTable")
            :expr-column (doto (node "exprColumn")
                           (.setCellValueFactory (fx/cell-value-callback :form)))
            :val-column (doto (node "valColumn")
                          (.setCellValueFactory (fx/cell-value-callback (comp fx/finite-pr-str :val))))
            :start-column (doto (node "startColumn")
                            (.setVisible false)
                            #_(.setCellValueFactory (fx/cell-value-callback (fn [x] (some->> x :rebl/start (.format tf))))))
            :elapsed-column (doto (node "elapsedColumn")
                              (.setCellValueFactory (fx/cell-value-callback :ms)))

            :source-column (doto (node "sourceColumn")
                             (.setCellValueFactory (fx/cell-value-callback :rebl/source)))
            :code-view (doto (node "codeView")
                         #_(.setZoom 1.2))
            :follow-editor-check (node "followEditorCheck")
            :eval-button (node "evalButton")
            :browser-choice (doto (node "browserChoice")
                              (.setConverter vc))
            :browse-pane (node "browsePane")
            ;:def-button
            ;;(doto (node "defButton") (.setDisable true))

            :def-text (doto (node "defText")
                        (.setPromptText "varname"))
            :nav-text (node "navText")
            :path-text (node "pathText")
            :ns-label (node "nsLabel")
            :viewer-choice (doto (node "viewerChoice")
                             (.setConverter vc))
            :view-pane (node "viewPane")
            :browse-tab-pane (node "browseTabPane")

            :eval-choice (node "evalChoice")
            :root-button (doto (node "rootButton")
                           (.setDisable true))
            :back-button (doto (node "backButton")
                           (.setDisable true))
            :fwd-button (doto (node "fwdButton")
                          (.setDisable true))
            :out-text (doto (node "outText")
                        (.setWrapText true))
            :tap-clear (node "tapClear")
            :tap-browse (node "tapBrowse")
            :tap-list tap-list
            :tap-list-view tap-list-view
            :tap-latest (node "tapLatest")}]
    (.setCellFactory tap-list-view (tap-cell-factory))
    (-> scene .getStylesheets (.add (str (io/resource "cognitect/rebl/fx.css"))))
    (.setItems tap-list-view tap-list)
    (.setTitle stage (:title ui))
    (.show stage)
    (-> (:code-view ui) .getEngine (.load (str (io/resource "cognitect/rebl/codeview.html"))))
    (wire-handlers ui)
    (monaco/register (:code-view ui) {})
    (tap exprs-mult exprs)
    (.setOnHidden stage (reify EventHandler (handle [_ _]
                                              (untap exprs-mult exprs)
                                              (async/close! exprs)
                                              (.close pwr))))
    ui))

(defn- init-remote [{:keys [exprs-mult proc]}]
  (fx/later
   #(try (let [ui (build-ui exprs-mult)
               exprs (:exprs ui)
               pwr (:eval-writer ui)
               prd (-> (java.io.PipedReader. pwr) clojure.lang.LineNumberingPushbackReader.)
               rds-call (make-sequencing-event-fn exprs :stringify-err fx/finite-pprint-str)
               rds-client (reify rds/IRemote
                            (remote-fetch [_ rid] (rds-call `(data.replicant.server.prepl/fetch ~rid)))
                            (remote-seq [_ rid] (rds-call `(data.replicant.server.prepl/seq ~rid)))
                            (remote-entry [_ rid k] (rds-call `(data.replicant.server.prepl/entry ~rid ~k)))
                            (remote-string [_ rid] (.println System/out (str :RSTR-UI)) (rds-call `(data.replicant.server.prepl/string ~rid)))
                            (remote-datafy [_ rid] (.println System/out (str :RDFY-UI)) (rds-call `(clojure.core.protocols/datafy ~rid)))
                            (remote-apply [_ rid args] (rds-call `(clojure.core/apply ~rid ~args))))]
           (async/thread (expr-loop ui))
           (async/thread (let [data-rdrs (merge *data-readers*
                                                {'r/id  #'rds-reader/rid-reader
                                                 'r/seq #'rds-reader/seq-reader
                                                 'r/kv  #'rds-reader/kv-reader
                                                 'r/vec #'rds-reader/vector-reader
                                                 'r/map #'rds-reader/map-reader
                                                 'r/set #'rds-reader/set-reader
                                                 'r/fn #'rds-reader/fn-reader})]
                           (try
                             (proc prd
                                   (fn [m] (async/put! exprs (assoc m :rebl/source (:title ui))))
                                   :readf (fn [rdr eof]
                                            (binding [rds-reader/*remote-client* rds-client
                                                      *data-readers*             data-rdrs]
                                              (let [[r s] (read+string rdr false eof)]
                                                ;(.println System/out (str "READF " s))
                                                r)))
                                   :valf (fn [s]
                                           ;(.println System/out (str "VALF " s))
                                           (binding [rds-reader/*remote-client* rds-client
                                                     *data-readers*             data-rdrs]
                                             (read-string s))))
                             (catch Throwable ex (prn {:ex ex}))))))
    (catch Throwable ex
      (println ex)))))

(defn- init-in-proc [{:keys [exprs-mult proc]}]
  (fx/later
    #(try (let [ui (build-ui exprs-mult)
                exprs (:exprs ui)
                pwr (:eval-writer ui)
                prd (-> (java.io.PipedReader. pwr) clojure.lang.LineNumberingPushbackReader.)]
            (async/thread (expr-loop ui))
            (async/thread (try
                            (proc prd (fn [m]
                                        (async/put! exprs (assoc m :rebl/source (:title ui)))))
                            (catch Throwable ex (prn {:ex ex})))))
      (catch Throwable ex
        (println ex)))))

(defn create [{:keys [mode] :as argmap}]
  (Platform/setImplicitExit false)
  (case mode
    :remote (init-remote argmap)
    :in-proc (init-in-proc argmap)
    (throw (ex-info (str "Could not initialize mode " mode)
                    {:mode mode})))
  nil)
