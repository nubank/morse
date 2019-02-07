;;   Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.renderers
  (:import
   [javafx.fxml FXMLLoader]
   [javafx.scene.control TableView TextArea])
  (:require
   [clojure.datafy :as datafy]
   [clojure.java.io :as io]
   [clojure.main :as main]
   [clojure.spec.alpha :as s]
   [cognitect.rebl :as rebl]
   [cognitect.rebl.impl.file :as file]
   [cognitect.rebl.fx :as fx]))

;;;;;;;;;;;;;;;;; table helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn set-table-maps
  [^TableView t maps ks val-cb]
  (fx/set-sortable-items t (fx/fxlist (into [] (map-indexed vector) (fx/finitify maps))))
  (-> t .getColumns (.setAll (cons (fx/index-column first)
                                   (map (fn [k] (fx/table-column (fx/finite-pr-str k) #(-> %1 second (get k))))
                                        ks))))
  (fx/add-selection-val-cb maps t val-cb))

(defn set-table-map-of-maps
  [^TableView t map-of-maps ks val-cb]
  (fx/set-sortable-items t (fx/fxlist (vec map-of-maps)))
  (-> t .getColumns (.setAll (cons (fx/table-column "key" first)
                                   (map (fn [k] (fx/table-column (fx/finite-pr-str k) #(-> %1 second (get k))))
                                        ks))))
  (fx/add-selection-val-cb map-of-maps t val-cb))

(defn set-table-map
  [^TableView t amap val-cb]
  (fx/set-sortable-items t (fx/fxlist (vec amap)))
  (-> t .getColumns (.setAll [(fx/table-column "key" key) (fx/table-column "val" val)]))
  (fx/add-selection-val-cb amap t val-cb))

(defn set-table-tuples
  [^TableView t tuples ks val-cb]
  (fx/set-sortable-items t (fx/fxlist (into [] (map-indexed vector) (fx/finitify tuples))))
  (-> t .getColumns (.setAll (cons (fx/index-column first)
                                   (map-indexed (fn [n k] (fx/table-column (fx/finite-pr-str k) #(-> %1 second (nth n))))
                                                ks))))
  (fx/add-selection-val-cb tuples t val-cb))

;; making an explicit collection of pairs so we have a row index
;; in hand, otherwise we get into silliness overriding concrete
;; TableCell to recover it later.
;; See https://stackoverflow.com/a/43102706/1456939
(defn set-table-coll
  [^TableView t coll val-cb]
  (if (instance? java.util.Set coll)
    (do
      (fx/set-sortable-items t (fx/fxlist (into [] (map vector) (fx/finitify coll))))
      (-> t .getColumns (.setAll [(fx/table-column "val" first)])))
    (do
      (fx/set-sortable-items t (fx/fxlist (into [] (map-indexed vector) (fx/finitify coll))))
      (-> t .getColumns (.setAll [(fx/index-column first) (fx/table-column "val" second)]))))
  (fx/add-selection-val-cb coll t val-cb))

;;;;;;;;;;;;;;;;; view/browse constructos and predicates ;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn plain-text-viewer
  [s]
  (fx/set-text (TextArea.) s))

(defn edn-viewer
  "Edn viewer.  Chooses rich text control if edn is small enough
to render efficiently, else plaintext."
  [edn]
  (let [text (fx/finite-pprint-str edn)]
    (if (< (count text) 10000)
      (fx/set-webview-text (javafx.scene.web.WebView.) text)
      (fx/set-text-area-text (TextArea.) text))))

(defn web-vb
  [url val-cb]
  (fx/set-webview (javafx.scene.web.WebView.) url val-cb))

(def spec-edn-viewer (comp edn-viewer s/form))

(defn throwable-map-vb
  ([ex] (throwable-map-vb ex nil))
  ([ex val-cb]
     (let [loader (FXMLLoader. (io/resource "cognitect/rebl/exception.fxml"))
           root (.load loader)
           names (.getNamespace loader)
           node (fn [id] (.get names id))]
       (doto (node "causeView")
         (.setText (-> ex main/ex-triage main/ex-str)))
       (if-let [data (:data ex)]
         (doto (node "exDataTable")
           (set-table-map data val-cb))
         (fx/hide-node (node "exDataBox")))
       (doto (node "viaTable")
         (set-table-maps (:via ex) [:type :message :at] nil))
       (doto (node "traceTable")
         (set-table-tuples (:trace ex) [:class :method :file] nil))
       root)))

(defn throwable-vb
  ([ex] (throwable-vb ex nil))
  ([ex val-cb] (throwable-map-vb (Throwable->map ex) val-cb)))

(defn map-vb
  [amap val-cb] (set-table-map (fx/table-view) amap val-cb))

(defn maps-keys
  [maps]
  (into [] (comp (filter fx/Map?) (map keys) cat (distinct) (take fx/coll-check-limit)) maps))

(defn coll-vb
  [alist val-cb] (set-table-coll (fx/table-view) alist val-cb))

(defn tuples-vb
  [tuples val-cb]
  (let [ks (range (count (first tuples)))]
    (set-table-tuples (fx/table-view) tuples ks val-cb)))

(defn maps-vb
  [maps val-cb] (set-table-maps (fx/table-view) maps (maps-keys maps) val-cb))

(defn map-of-maps-vb
  [map-of-maps val-cb] (set-table-map-of-maps (fx/table-view) map-of-maps (maps-keys (vals map-of-maps)) val-cb))

(defn file-top
  [s]
  (let [f (-> s meta ::datafy/obj)]
    (fx/set-text (TextArea.) (file/bounded-slurp f 10000))))

(defn file-browse
  [s]
  (let [url (-> s meta ::datafy/obj io/as-url)]
    (fx/set-webview (javafx.scene.web.WebView.) url)))

(rebl/update-viewers {:rebl/data-as-edn {:pred #'any? :ctor #'edn-viewer}
                      :rebl/code {:pred #'fx/code? :ctor #'edn-viewer}
                      :rebl/text {:pred #'string? :ctor #'plain-text-viewer}
                      :rebl/url {:pred #'fx/url? :ctor #'web-vb}
                      :rebl/html {:pred #'string? :ctor #'web-vb}
                      :rebl/spec-edn {:pred #'s/spec? :ctor #'spec-edn-viewer}
                      :rebl/map {:pred #'fx/Map? :ctor #'map-vb}
                      :rebl/coll {:pred #'fx/Coll? :ctor #'coll-vb}
                      :rebl/tuples {:pred #'fx/tuples? :ctor #'tuples-vb}
                      :rebl/maps {:pred #'fx/maps? :ctor #'maps-vb}
                      :rebl/map-of-maps {:pred #'fx/map-of-maps? :ctor #'map-of-maps-vb}
                      :rebl/throwable-map {:ctor #'throwable-map-vb :pred #'fx/throwable-map?}
                      :rebl/throwable {:ctor #'throwable-vb :pred #'fx/throwable?}
                      :rebl.file/top {:ctor #'file-top :pred #'file/datafied-file?}
                      ;; :rebl.file/browse {:ctor #'file-browse :pred #'file/datafied-file?}
                      })

(rebl/update-browsers {:rebl/map {:pred #'fx/Map? :ctor #'map-vb}
                       :rebl/url {:pred #'fx/url? :ctor #'web-vb}
                       :rebl/html {:pred #'string? :ctor #'web-vb}
                       :rebl/coll {:pred #'fx/Coll? :ctor #'coll-vb}
                       :rebl/tuples {:pred #'fx/tuples? :ctor #'tuples-vb}
                       :rebl/maps {:pred #'fx/maps? :ctor #'maps-vb}
                       :rebl/map-of-maps {:pred #'fx/map-of-maps? :ctor #'map-of-maps-vb}
                       })
