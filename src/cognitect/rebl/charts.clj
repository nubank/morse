;;   Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.charts
  (:import
   [javafx.fxml FXMLLoader]
   [javafx.scene.chart AreaChart BarChart CategoryAxis LineChart NumberAxis ScatterChart StackedAreaChart StackedBarChart XYChart XYChart$Data XYChart$Series])
  (:require
   [clojure.java.io :as io]
   [cognitect.rebl :as rebl]
   [cognitect.rebl.fx :as fx]
   [cognitect.rebl.ui :as ui]))

(defn- set-all
  [container data]
  (.setAll container (fx/fxlist data)))

(defn- xy-data
  [[x y extra :as v] [convx convy]]
  (let [convx (or convx identity)
        convy (or convy identity)]
    (case (count v)
          2 (XYChart$Data. (convx x) (convy y))
          3 (XYChart$Data. (convx x) (convy y) extra))))

;; currently just a stub
(defn histogram
  [coll]
  (let [x (CategoryAxis.)
        y (NumberAxis.)
        bc (BarChart. x y)
        ser (XYChart$Series.)]
    (.setLabel x "Range")
    (.setLabel y "Value")
    (.setName ser "Histogram")
    (-> ser .getData (set-all (map xy-data [["a" 1] ["b" 2]])))
    (-> bc .getData (set-all [ser]))
    bc))

(defn xy-series
  [{:keys [name convx convy]} tuples]
  (let [ser (XYChart$Series.)]
    (.setName ser (fx/finite-str name))
    (-> ser .getData (set-all (map #(xy-data % [convx convy]) tuples)))
    ser))

(defn x-axis-for
  [chart]
  (if (get #{:bar :stacked-bar} chart)
    (CategoryAxis.)
    (NumberAxis.)))

(declare xy-type)
(defn sampled-set
  [x f]
  (->> (fx/sample x fx/coll-check-limit)
       (map f)
       frequencies
       keys
       (into #{})))

(defn xy-type
  [x]
  (cond
   (number? x) :num
   (string? x) :category
   (keyword? x) :category
   (symbol? x) :category
   (fx/Coll? x) (let [topcount (bounded-count 4 x)
                      subtype (sampled-set x xy-type)]
                  (if (or (= #{:coll-of-nums} subtype)
                          (= #{:coll-of-nums :coll-of-categories} subtype))
                    (let [subcounts (sampled-set x count)]
                      ;; 2/3 x 2/3 colls are ambiguous, making a choice
                      #_(prn {:topcount topcount :subtype subtype :subcounts subcounts})
                      (cond (= #{2} subcounts) :coll-of-pairs
                            (= #{3} subcounts) :coll-of-triples
                            (= 2 topcount) :pair-of-colls
                            (= 3 topcount) :triple-of-colls))
                    (cond (= #{:num} subtype) :coll-of-nums
                          (= #{:category} subtype) :coll-of-categories
                          (= #{:num :category} subtype) :coll-of-vals)))))

;; TODO  :coll-of-categories :pair-of-colls :triple-of-colls :coll-of-pairs :coll-of-triples
(defn xy-chartable?
  [x]
  (contains? #{:coll-of-nums :coll-of-pairs} (xy-type x)))

(defmulti to-xy-series-data (fn [options data] (xy-type data)))

(defmethod to-xy-series-data :coll-of-nums
  [opts x]
  (xy-series opts (map-indexed vector x)))

(defmethod to-xy-series-data :coll-of-pairs
  [opts x]
  (xy-series opts x))

(defn series-pair-chart
  [{:keys [x-label y-label type] :or {x-label "x" y-label "y"}}
   serieses]
  (let [x (x-axis-for type)
        y (NumberAxis.)
        c (case type
                :line (LineChart. x y)
                :area (AreaChart. x y)
                :scatter (ScatterChart. x y)
                :bar (BarChart. x y)
                :stacked-bar (StackedBarChart. x y)
                :stacked-area (StackedAreaChart. x y))]
    (.setLabel x x-label)
    (.setLabel y y-label)
    (-> c .getData (set-all (map (fn [[name data]]
                                   (to-xy-series-data {:name name} data))
                                 serieses)))
    c))

(defn chart-chosen
  [pane kw coll]
  (ui/update-pane pane (series-pair-chart {:type kw} [["Data" coll]])))

(defn series-pair-chart-v
  [coll]
  (let [loader (FXMLLoader. (io/resource "cognitect/rebl/series-pair-chart.fxml"))
        root (.load loader)
        names (.getNamespace loader)
        node (fn [id] (.get names id))
        chart-choice (node "chartChoice")
        pane (node "chartView")]
    (-> chart-choice .valueProperty (.addListener (fx/change-listener (fn [ob ov nv] (chart-chosen pane nv coll)))))
    (ui/update-choice chart-choice [:area :line :scatter] :scatter)
    root))

(rebl/update-viewers {:charts/series-pair {:pred xy-chartable? :ctor series-pair-chart-v}})



