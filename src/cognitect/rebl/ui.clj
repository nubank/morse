;;   Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.ui
  (:require
   [cognitect.rebl :as rebl]
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [clojure.spec.alpha :as s]
   [clojure.set :as set]
   [clojure.core.async :as async :refer [<!! chan tap untap]])
  (:import [javafx.fxml FXMLLoader]
           [javafx.scene Node Scene]
           [javafx.event EventHandler]
           [javafx.application Platform]
           [javafx.collections FXCollections]
           [javafx.scene.input KeyEvent KeyCodeCombination KeyCode KeyCombination$Modifier]
           [javafx.scene.control.cell MapValueFactory]
           [javafx.scene.control TableView TableColumn Tooltip]
           [javafx.util Callback]
           [javafx.beans.property ReadOnlyObjectWrapper]))

(def coll-check-limit 101)

(defn fx-later [f]
  (Platform/runLater f))

(defn current-ui [pane]
  (let [cs (.getChildren pane)]
    (when-not (empty? cs)
      (.get cs 0))))

(defn sample
  "Returns a seq of n items from coll. Picks the first items
if coll not indexed?, otherwise sets a step size and uses nth
to get elements from throughout the collection"
  [coll n]
  (if (indexed? coll)
    (let [ct (count coll)]
      (let [step (max 1 (long (/ ct n)))]
        (map #(nth coll %) (take (min n ct) (iterate #(+ % step) 0)))))
    (take n coll)))

(defn- jaccard-index
  "Returns the Jaccard index for distance between two Clojure sets"
  [s1 s2]
  (/ (double (count (set/intersection s1 s2)))
     (count (set/union s1 s2))))

(defn- maps-jaccard
  "Given coll of n maps, return n-1 Jaccard indexes for adjacent
comparisons."
  [maps]
  (->> maps
       (partition 2 1)
       (map (fn [[m1 m2]] (jaccard-index (into #{} (keys m1))
                                         (into #{} (keys m2)))))))

(defn uniformish?
  "Quick and dirty test for maps having mostly similar keys"
  [maps]
  (every? #(< 0.9 %) (maps-jaccard maps)))

(defn fxlist [coll]
  (FXCollections/observableList coll))

;; see e.g. https://stackoverflow.com/questions/28558165/javafx-setvisible-doesnt-hide-the-element
(defn hide-node
  [^Node n]
  (doto n
    (.setManaged false)
    (.setVisible false)))

(defn finitify
  "Turn a list into a finite indexed collection"
  [coll]
  (if (vector? coll)
    coll
    (into [] (take (or *print-length* 100000) coll))))

(defn finite-pprint-str
  "Returns a pretty printed string for e.g. an edn viewer"
  [x]
  (binding [pp/*print-right-margin* 80
            *print-length* 10000
            *print-level* 20]
    (with-out-str (pp/pprint x))))

(defn finite-pr-str
  "Returns a truncated string rep for e.g. a table cell"
  [x]
  (binding [*print-length* 5
            *print-level* 2]
    (pr-str x)))

(defn reset-code [code-view]
  (-> (.getEngine code-view)
      (.executeScript "document.cm")
      (.call "setValue" (to-array [""]))))

(defn set-code [code-view code]
  (-> (.getEngine code-view)
      (.executeScript "document.cm")
      (.call "setValue" (to-array [(finite-pprint-str code)]))))

(defn get-code [code-view]
  (-> (.getEngine code-view)
      (.executeScript "document.cm")
      (.call "getValue" (to-array []))))

(defn change-listener
  "makes a javafx.beans.value.ChangeListener given a function of observable,oldval,newval"
  [f]
  (reify javafx.beans.value.ChangeListener
         (changed [_ ob oldval newval]
                  (f ob oldval newval))))

(defn add-selection-listener
  "adds a selection listener to table given f, a fn of the index and
  the row value. returns table"
  [table f]
  (-> table .getSelectionModel .selectedIndexProperty
      (.addListener (change-listener (fn [ob oldidx nidx]
                                       (when (not= nidx -1)
                                         (f nidx (-> table .getItems (.get nidx))))))))
  table)

(defn cell-value-callback
  "Returns a Callback that applies finite-pr-str to f of cell value."
  [f]
  (reify Callback
         (call [_ cdf]
               (ReadOnlyObjectWrapper. (finite-pr-str (f (.getValue cdf)))))))

(defn table-column
  "returns a TableColumn with given name and CellValueFactory callback
  based on finitely printing the result of f, a fn of the row"
  [name f]
  (doto (TableColumn. name)
    (.setCellValueFactory (cell-value-callback f))))

(defn set-webview-edn
  [wv v]
  (let [eng (.getEngine wv)]
    (-> eng .getLoadWorker .stateProperty
        (.addListener
         (reify javafx.beans.value.ChangeListener
                (changed [_ ob oldv newv]
                         (when (= newv javafx.concurrent.Worker$State/SUCCEEDED)
                           (set-code wv v))))))
    (.load eng (str (io/resource "codeview.html")))
    wv))

;; per Sorting at https://docs.oracle.com/javase/8/javafx/api/javafx/scene/control/TableView.html
(defn set-sortable-items
  [^TableView t items]
  (let [sorted (javafx.collections.transformation.SortedList. items)]
    (-> sorted .comparatorProperty (.bind (.comparatorProperty t)))
    (.setItems t sorted)))

(defn set-table-maps
  [^TableView t maps ks val-cb]
  (set-sortable-items t (fxlist (into [] (map-indexed vector) (finitify maps))))
  (-> t .getColumns (.setAll (cons (table-column "idx" first)
                                   (map (fn [k] (table-column (finite-pr-str k) #(-> %1 second (get k))))
                                        ks))))
  (when val-cb
    (add-selection-listener t (fn [idx [k v]] (val-cb t idx v)))
    (fx-later #(-> t .getSelectionModel .selectFirst)))
  t)

(defn set-table-map
  [^TableView t amap val-cb]
  (set-sortable-items t (fxlist (vec amap)))
  (-> t .getColumns (.setAll [(table-column "key" key) (table-column "val" val)]))
  (when val-cb
    (add-selection-listener t (fn [idx [k v]] (val-cb t k v)))
    (fx-later #(-> t .getSelectionModel .selectFirst)))
  t)

(defn set-table-tuples
  [^TableView t tuples ks val-cb]
  (set-sortable-items t (fxlist (into [] (map-indexed vector) (finitify tuples))))
  (-> t .getColumns (.setAll (cons (table-column "idx" first)
                                   (map-indexed (fn [n k] (table-column (finite-pr-str k) #(-> %1 second (nth n))))
                                                ks))))
  (when val-cb
    (add-selection-listener t (fn [idx [k v]] (val-cb t idx v)))
    (fx-later #(-> t .getSelectionModel .selectFirst)))
  t)

;; making an explicit collection of pairs so we have a row index
;; in hand, otherwise we get into silliness overriding concrete
;; TableCell to recover it later.
;; See https://stackoverflow.com/a/43102706/1456939
(defn set-table-coll
  [^TableView t coll val-cb]
  (set-sortable-items t (fxlist (into [] (map-indexed vector) (finitify coll))))
  (-> t .getColumns (.setAll [(table-column "idx" first) (table-column "val" second)]))
  (when val-cb
    (add-selection-listener t (fn [idx [k v]] (val-cb t idx v)))
    (fx-later #(-> t .getSelectionModel .selectFirst)))
  t)

(defn edn-viewer [edn]
  (set-webview-edn (javafx.scene.web.WebView.) edn))

(def spec-edn-viewer (comp edn-viewer s/form))

(defn throwable-map?
  [x]
  (and (map? x) (:cause x) (:via x) (:trace x)))

(defn throwable-map-vb
  ([ex] (throwable-map-vb ex nil))
  ([ex val-cb]
     (let [loader (FXMLLoader. (io/resource "exception.fxml"))
           root (.load loader)
           names (.getNamespace loader)
           node (fn [id] (.get names id))]
       (doto (node "causeView")
         (.setText (:cause ex)))
       (if-let [data (:data ex)]
         (doto (node "exDataTable")
           (set-table-map data val-cb))
         (hide-node (node "exDataBox")))
       (doto (node "viaTable")
         (set-table-maps (:via ex) [:type :message :at] nil))
       (doto (node "traceTable")
         (set-table-tuples (:trace ex) [:class :method :file] nil))
       root)))

(defn var-vb
  [v val-cb]
  (let [loader (FXMLLoader. (io/resource "var.fxml"))
        root (.load loader)
        names (.getNamespace loader)
        node (fn [id] (.get names id))
        m (meta v)
        val @v]
    (if-let [d (:doc m)]
      (doto (node "docView")
        (.setText d))
      (hide-node (node "docBox")))
    (let [other-m (dissoc m :doc)]
      (doto (node "metaTable")
        (set-table-map other-m nil)))
    (doto (node "ednView")
      (set-webview-edn val))
    (fx-later #(val-cb root :val val))
    root))

(defn map-vb
  [amap val-cb] (set-table-map (TableView.) amap val-cb))

(defn namespace?
  [x]
  (instance? clojure.lang.Namespace x))

(defn ns-publics-vb
  [v val-cb]
  (map-vb (ns-publics v) val-cb))

(def Map? #(instance? java.util.Map %1))
(def Coll? #(instance? java.util.Collection %1))

(def max-cols 100)

(defn tuples?
  [coll]
  (and (Coll? coll)
       (seq coll)
       (let [e (first coll)]
         (and (sequential? e)
              (let [bc (partial bounded-count max-cols)
                    cnt (bc e)]
                (every? #(and (sequential? %1)
                              (= cnt (bc %1)))
                        (take 100 coll)))))))

(defn uniformish-maps?
  [coll]
  (and (Coll? coll)
       (seq coll)
       (let [samp (sample coll coll-check-limit)]
         (and (every? Map? samp)
              (uniformish? samp)))))

(defn maps-keys
  [maps]
  (into [] (comp (filter Map?) (map keys) cat (distinct) (take max-cols)) maps))

(defn coll-vb
  [alist val-cb] (set-table-coll (TableView.) alist val-cb))

(defn tuples-vb
  [tuples val-cb]
  (let [ks (range (count (first tuples)))]
    (set-table-tuples (TableView.) tuples ks val-cb)))

(defn maps-vb
  [maps val-cb] (set-table-maps (TableView.) maps (maps-keys maps) val-cb))

(swap! rebl/registry update-in [:viewers]
       assoc
       :rebl/edn {:pred #'any? :ctor #'edn-viewer}
       :rebl/spec-edn {:pred #'s/spec? :ctor #'spec-edn-viewer}
       :rebl/map {:pred #'Map? :ctor #'map-vb}
       :rebl/coll {:pred #'Coll? :ctor #'coll-vb}
       :rebl/tuples {:pred #'tuples? :ctor #'tuples-vb}
       :rebl/maps {:pred #'uniformish-maps? :ctor #'maps-vb}
       :rebl/exception {:ctor #'throwable-map-vb :pred #'throwable-map?}
       :rebl/var {:ctor #'var-vb :pred #'var?}
       :rebl/ns-publics {:ctor #'ns-publics-vb :pred #'namespace?})

(swap! rebl/registry update-in [:browsers]
       assoc
       :rebl/map {:pred #'Map? :ctor #'map-vb}
       :rebl/var {:ctor #'var-vb :pred #'var?}
       :rebl/coll {:pred #'Coll? :ctor #'coll-vb}
       :rebl/tuples {:pred #'tuples? :ctor #'tuples-vb}
       :rebl/maps {:pred #'uniformish-maps? :ctor #'maps-vb}
       :rebl/ns-publics {:ctor #'ns-publics-vb :pred #'namespace?})

(declare val-selected)

(defn viewer-for
  "returns {:keys [view-ui view-options view-choice]}"
  [ui val]
  (let [{:keys [viewers pref]} (rebl/viewers-for val)
        p (pref viewers)]
    {:view-ui (if (rebl/is-browser? pref)
                ((:ctor p) val (partial val-selected ui))
                ((:ctor p) val))
     ;;incorporate :id for choice control
     :view-options (-> viewers vals vec)
     :view-choice p}))

(defn update-choice [control options choice]
  (-> control (.setItems (fxlist options)))
  (-> control (.setValue choice)))

(defn update-pane [pane ui]
  (-> pane .getChildren (.setAll [ui])))

(defn view [{:keys [state view-pane viewer-choice fwd-button] :as ui} path-seg val]
  (let [viewer (viewer-for ui val)]
    (swap! state merge (assoc viewer :path-seg path-seg :view-val val))
    (update-choice viewer-choice (:view-options viewer) (:view-choice viewer))
    (update-pane view-pane (:view-ui viewer))
    (.setDisable fwd-button (-> val rebl/browsers-for :browsers empty?))))

(defn val-selected
  [{:keys [view-pane state] :as ui} node path-seg val]
  (if (identical? (current-ui view-pane) node)
    (swap! state assoc :on-deck {:path-seg path-seg :val val})
    (view ui path-seg val)))

(defn viewer-chosen [{:keys [state view-pane] :as ui} choice]
  (let [{:keys [view-choice view-val]} @state]
    (when (and choice (not= view-choice choice))
      (let [vw ((:ctor choice) view-val)]
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
  (swap! state merge (assoc browser :browse-val val))
  (update-choice browser-choice (:browse-options browser) (:browse-choice browser))
  (update-pane browse-pane (:browse-ui browser))
  (.requestFocus (:browse-ui browser)))

(defn browse [ui val]
  (browse-with (browser-for ui val)))

(defn browser-chosen [{:keys [state browse-pane] :as ui} choice]
  (let [{:keys [browse-choice browse-val]} @state]
    (when (and choice (not= browse-choice choice))
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
    (-> eval-table .getItems (.setAll ehist))
    (swap! state merge browser)
    (update-choice browser-choice [bc] bc)
    (update-pane browse-pane eval-table)
    ;;(set-code code-view (-> ehist first :expr))
    (.setDisable root-button true)
    (.setDisable back-button true)
    (-> eval-table .getSelectionModel .selectFirst)
    (.requestFocus eval-table)))

(defn load-expr [{:keys [eval-history code-view] :as ui} n]
  (if (= -1 n)
    (reset-code code-view)
    (set-code code-view (-> @eval-history (nth n) :expr))))

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
                  (fx-later #(rtz ui)))
            (recur)))))))

(defonce ^:private ui-count (atom 0))

(defn eval-pressed [{:keys [code-view exprs expr-ord] :as ui}]
  ;;TODO handle bad form
  (reset! expr-ord -1)
  (let [code (get-code code-view)
        form (read-string code)]
    (reset-code code-view)
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

(defn wire-handlers [{:keys [root-button back-button fwd-button eval-button
                             viewer-choice browser-choice
                             scene eval-table code-view browse-pane view-pane] :as ui}]
  (let [wire-button (fn [f b]
                      (.setOnAction b (reify EventHandler (handle [_ e] (f)))))
        wire-key (fn [f k & cs]
                   (.addEventFilter scene KeyEvent/KEY_PRESSED
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
    (wire-key #(eval-pressed ui) KeyCode/ENTER KeyCodeCombination/CONTROL_DOWN)
    ;;sending focus to parent pane doesn't work
    (wire-key #(-> browse-pane current-ui .requestFocus) KeyCode/B KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(.requestFocus browser-choice) KeyCode/B KeyCodeCombination/CONTROL_DOWN KeyCodeCombination/SHIFT_DOWN)
    (wire-key #(-> view-pane current-ui .requestFocus) KeyCode/V KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(.requestFocus viewer-choice) KeyCode/V KeyCodeCombination/CONTROL_DOWN KeyCodeCombination/SHIFT_DOWN)
    (wire-key #(.requestFocus code-view) KeyCode/R KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(when-not (.isDisabled fwd-button) (fwd-pressed ui)) KeyCode/RIGHT KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(when-not (.isDisabled back-button) (back-pressed ui)) KeyCode/LEFT KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(when-not (.isDisabled root-button) (rtz ui)) KeyCode/LEFT
              KeyCodeCombination/CONTROL_DOWN KeyCodeCombination/SHIFT_DOWN)
    (wire-key #(prev-expr ui) KeyCode/UP KeyCodeCombination/CONTROL_DOWN)
    (wire-key #(next-expr ui) KeyCode/DOWN KeyCodeCombination/CONTROL_DOWN)
    ;;buttons
    (wire-button #(eval-pressed ui) eval-button)
    (wire-button #(fwd-pressed ui) fwd-button)
    (wire-button #(back-pressed ui) back-button)
    (wire-button #(rtz ui) root-button)
    ;;choice controls
    (-> viewer-choice .valueProperty (.addListener (change-listener (fn [ob ov nv] (viewer-chosen ui nv)))))
    (-> browser-choice .valueProperty (.addListener (change-listener (fn [ob ov nv] (browser-chosen ui nv)))))
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
    (add-selection-listener eval-table (fn [idx row]
                                         (let [{:keys [expr val]} row]
                                           ;;(set-code code-view expr)
                                           (view ui idx val))))))

(defn- init [{:keys [exprs-mult]}]
  (fx-later
   #(try (let [loader (FXMLLoader. (io/resource "rebl.fxml"))
               root (.load loader)               
               names (.getNamespace loader)
               node (fn [id] (.get names id))
               scene (Scene. root 1200 800)
               stage (javafx.stage.Stage.)
               _ (.setScene stage scene)
               exprs (chan 10)
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
                                 (.setItems (fxlist (java.util.ArrayList.))))
                   :expr-column (doto (node "exprColumn")
                                  (.setCellValueFactory (cell-value-callback :expr)))
                   :val-column (doto (node "valColumn")
                                 (.setCellValueFactory (cell-value-callback :val)))
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
           (async/thread (clojure.main/with-bindings (expr-loop ui))))
         (catch Throwable ex
           (println ex)))))

(defn create [argmap]
  (Platform/setImplicitExit false)
  (init argmap)
  nil)
