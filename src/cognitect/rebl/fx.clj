;;   Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.fx
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [clojure.set :as set]
   [clojure.string :as str]
   [cognitect.rebl :as rebl])
  (:import
   [javafx.application Platform]
   [javafx.beans.property ReadOnlyObjectWrapper]
   [javafx.collections FXCollections]
   [javafx.scene Node]
   [javafx.scene.control TableView TableColumn]
   [javafx.util Callback]))

;;;;;;;;;;;;;;;;; data manipulation ;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def coll-check-limit 101)

(def Map? #(instance? java.util.Map %1))
(def Coll? #(instance? java.util.Collection %1))
(def url? #(and (instance? java.net.URL %1)))
(def clojure-code? #(and (instance? java.util.List %)
                         (symbol? (first %))))

(defn table-view []
  (doto (TableView.)
    (.setMaxWidth Double/MAX_VALUE)
    (.setMaxHeight Double/MAX_VALUE)))

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

(defn jaccard-index
  "Returns the Jaccard index for distance between two Clojure sets"
  [s1 s2]
  (/ (double (count (set/intersection s1 s2)))
     (count (set/union s1 s2))))

(defn map-keys-jaccard-indices
  "Given coll of n maps, return a seq of jaccard indexes for each
map's keys against the union of all keys."
  [maps]
  (let [keyset #(into #{} (keys %))
        union-keys (transduce (map keyset) set/union maps)]
    (->> maps
         (map (fn [m] (jaccard-index union-keys (keyset m)))))))

(defn uniformish?
  "Quick and dirty test for maps having mostly similar keys"
  [maps]
  (every? #(< 0.6 %) (map-keys-jaccard-indices maps)))

(defn finitify
  "Turn a list into a finite indexed collection"
  [coll]
  (if (vector? coll)
    coll
    (into [] (take (or *print-length* 100000) coll))))

(defn finite-pprint-str
  "Returns a finite pretty printed string for e.g. an edn viewer"
  [x]
  (binding [pp/*print-right-margin* 72
            *print-length* 1000
            *print-level* 20]
    (with-out-str (pp/pprint x))))

(defn normalize-whitespace
  "Flatten all whitespace runs to single space char."
  [s]
  (str/replace s #"\s+" " "))

(defn ellipsize
  "Ellipsize strings longer than n to fit into n chars"
  [s n]
  (if (<= (count s) n)
    s
    (str (subs s 0 (- n 3)) "...")))

(defn finite-str
  "Returns a finite string rep for e.g. a label or header."
  [s n]
  (-> s normalize-whitespace (ellipsize n)))

(defn finite-pr-str
  "Returns a finite string rep for e.g. a table cell"
  [x]
  (binding [*print-length* 5
            *print-level* 5]
    (-> x pr-str (finite-str 1000))))

(defn tuples?
  [coll]
  (and (Coll? coll)
       (seq coll)
       (let [e (first coll)]
         (and (or (indexed? e) (sequential? e))
              (let [bc (partial bounded-count coll-check-limit)
                    cnt (bc e)]
                (every? #(and (or (indexed? e) (sequential? %1))
                              (= cnt (bc %1)))
                        (take 100 coll)))))))

(defn maps?
  [coll]
  (and (Coll? coll)
       (seq coll)
       (let [samp (sample coll coll-check-limit)]
         (every? Map? samp))))

(defn numbers?
  [x]
  (and (Coll? x)
       (seq x)
       (every? number? (sample x coll-check-limit))))

(defn seq-numbers?
  [x]
  (and (sequential? x)
       (seq x)
       (every? number? (sample x coll-check-limit))))

(defn number-pair?
  [x]
  (and (Coll? x)
       (= 2 (bounded-count 2 x))
       (number? (first x))
       (number? (second x))))

(defn number-pairs?
  [x]
  (and (Coll? x)
       (seq x)
       (every? number-pair? (sample x coll-check-limit))))

(defn keyed-number-pairs?
  [x]
  (and (map? x)
       (<= (count x) 20)
       (every? number-pairs? (vals x))))

(defn map-of-maps?
  [m]
  (and (Map? m) (maps? (vals m))))

(defn throwable-map?
  [x]
  (and (map? x) (not (sorted? x)) (:cause x) (:via x) (:trace x)))

(defn throwable?
  [x]
  (instance? Throwable x))

(defn namespace?
  [x]
  (instance? clojure.lang.Namespace x))

(defn atom?
  [x]
  (instance? clojure.lang.IAtom x))

;;;;;;;;;;;;;;;;; fx helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn later [f]
  (Platform/runLater f))

(defn current-ui [pane]
  (let [cs (.getChildren pane)]
    (when-not (empty? cs)
      (.get cs 0))))

(defn fxlist [coll]
  (FXCollections/observableList coll))

;; see e.g. https://stackoverflow.com/questions/28558165/javafx-setvisible-doesnt-hide-the-element
(defn hide-node
  [^Node n]
  (doto n
    (.setManaged false)
    (.setVisible false)))

;; N.B. controls that have .setText do not have a common base interface
(defn set-text
  "Set text of a control to stringified x. Returns control."
  [control x]
  (doto control
    (.setText (str x))))

(defn reset-code [code-view]
  (-> (.getEngine code-view)
      (.executeScript "document.cm")
      (.call "setValue" (to-array [""]))))

(defn set-code [code-view code]
  (-> (.getEngine code-view)
      (.executeScript "document.cm")
      (.call "setValue" (to-array [code]))))

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

(defn add-selection-val-cb
  "adds val-cb as a selection listener on table, returning table.
Assumes that the value stored in the table is a [position value]
pair."
  [data t val-cb]
  (when val-cb
    (add-selection-listener t (fn [_ [pos val]] (val-cb t pos val)))
    (-> t .getSelectionModel .selectFirst))
  t)

(defn cell-value-callback
  "Returns a Callback that applies f to a cell value."
  [f]
  (reify Callback
         (call [_ cdf]
               (ReadOnlyObjectWrapper. (f (.getValue cdf))))))

(defn index-column
  "returns an index column based on the value of f, a fn of the row"
  [f]
  (doto (TableColumn. "idx")
    (.setCellValueFactory (cell-value-callback f))))

(defn table-column
  "returns a TableColumn with given name and CellValueFactory callback
  based on finitely printing the result of f, a fn of the row"
  [name f]
  (doto (TableColumn. name)
    (.setCellValueFactory (cell-value-callback (comp finite-pr-str f)))))

(defn- get-elements
  [doc tag-name]
  (let [els (.getElementsByTagName doc tag-name)]
    (into [] (map #(.item els %) (range (.getLength els))))))

(defn- load-engine-content
  [engine stuff]
  (cond
   (string? stuff)
   (.loadContent engine stuff)

   (url? stuff)
   (.load engine (str stuff))))

(defn add-webview-cb
  [wv val-cb]
  (let [eng (.getEngine wv)
        doc (.getDocument eng)
        els (get-elements doc "a")]
    #_(prn {:doc doc :els (count els)})
    (doseq [el els]
      (when-let [href (.getHref el)]
        (.addEventListener el
                           "click"
                           (reify org.w3c.dom.events.EventListener
                                  (handleEvent
                                   [_ ev]
                                   (when val-cb
                                     (val-cb wv href (java.net.URL. href)))
                                   (.preventDefault ev)))
                           false)))))

(defn set-webview
  ([wv stuff]
     (set-webview wv stuff nil))
  ([wv stuff val-cb]
     (let [eng (.getEngine wv)]
       (load-engine-content eng stuff)
       (-> eng .getLoadWorker .stateProperty
           (.addListener
            (reify javafx.beans.value.ChangeListener
                   (changed [_ ob oldv newv]
                            (when (= newv javafx.concurrent.Worker$State/SUCCEEDED)
                              (add-webview-cb wv val-cb))))))
       wv)))

(defn set-webview-text
  "N.B. This performs poorly with large text strings."
  [wv text]
  (let [eng (.getEngine wv)]
    (-> eng .getLoadWorker .stateProperty
        (.addListener
         (reify javafx.beans.value.ChangeListener
                (changed [_ ob oldv newv]
                         (when (= newv javafx.concurrent.Worker$State/SUCCEEDED)
                           (set-code wv text))))))
    (.load eng (str (io/resource "cognitect/rebl/codeview.html")))
    wv))

(defn set-text-area-text
  [ta text]
  (doto ta
    (.setFont (javafx.scene.text.Font. "Monaco" 14.0))
    (.setText text)))

;; per Sorting at https://docs.oracle.com/javase/8/javafx/api/javafx/scene/control/TableView.html
(defn set-sortable-items
  [^TableView t items]
  (let [sorted (javafx.collections.transformation.SortedList. items)]
    (-> sorted .comparatorProperty (.bind (.comparatorProperty t)))
    (.setItems t sorted)))




