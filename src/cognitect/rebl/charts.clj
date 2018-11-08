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

(defn distinct-values
  "Returns the set of distinct values of (f x) where x is sampled
from coll."
  [coll f]
  (->> (fx/sample coll fx/coll-check-limit)
       (map f)
       (into #{})))

(defn scalar-type
  [x]
  (cond
   (number? x) :num
   (string? x) :name
   (keyword? x) :name
   (symbol? x) :name))

(defn- sample [coll] (fx/sample coll fx/coll-check-limit))
(defn- mostly? [pred coll] (every? pred (sample coll)))

(defn twople?
  [x]
  (= 2 (bounded-count 2 x)))

(defn coll-of-nums?
  [x]
  (and (instance? java.util.Collection x)
       (mostly? number? x)))

(defn scalar-num-pair?
  [x]
  (and (instance? java.util.Collection x)
       (twople? x)
       (scalar-type (first x))
       (number? (second x))))

(defn scalar->num?
  [x]
  (and (instance? java.util.Map x)
       (mostly? scalar-num-pair? x)))

(defn coll-of-pairs?
  [x]
  (and (instance? java.util.Collection x)
       (mostly? scalar-num-pair? x)))

(defn pair-of-colls?
  [x]
  (and (instance? java.util.Collection x)
       (twople? x)
       (instance? java.util.Collection (first x))
       (instance? java.util.Collection (second x))
       (mostly? scalar-type (first x))
       (mostly? number? (second x))))

(defn xy-type
  [x]
  (cond
   (coll-of-nums? x) :coll-of-nums
   (scalar->num? x) :scalar->num
   (coll-of-pairs? x) :coll-of-pairs
   (pair-of-colls? x) :pair-of-colls))

(defn xy-chartable?
  [x]
  (boolean (xy-type x)))

(defmulti to-xy-series-data (fn [data] (xy-type data)))
(defmethod to-xy-series-data :coll-of-nums [x] (map-indexed vector x))
(defmethod to-xy-series-data :coll-of-pairs [x] x)
(defmethod to-xy-series-data :scalar->num [x] x)
(defmethod to-xy-series-data :pair-of-colls [[xs ys]] (map vector xs ys))

(defn x-axis-for
  [type]
  (if (contains? {:bar :stacked-bar} type)
    (CategoryAxis.)
    (NumberAxis.)))

(defn transform-x-axis
  [type xy-series-data]
  (if (contains? {:bar :stacked-bar} type)
    (map (fn [[x y]]
           [(str x) y])
         xy-series-data)
    xy-series-data))

(defn- xy-data
  [{:keys [convx convy flipped?]} [x y extra :as v]]
  (let [x ((or convx identity) x)
        y ((or convy identity) y)
        [x y] (if flipped? [y x] [x y])]
    (case (count v)
          2 (XYChart$Data. x y)
          3 (XYChart$Data. y x extra))))

(defn xy-series
  [{:keys [name] :as opts} tuples]
  (let [ser (XYChart$Series.)]
    (.setName ser (fx/finite-str name 40))
    (-> ser .getData (set-all (map #(xy-data opts %) tuples)))
    ser))

(defn xy-chart
  [{:keys [x-label y-label type flipped?] :or {x-label "x" y-label "y"}}
   serieses]
  (let [xy-datas (map to-xy-series-data serieses)
        x (doto (x-axis-for type) (.setLabel x-label))
        y (doto (NumberAxis.) (.setLabel y-label))
        [x y] (if flipped? [y x] [x y])
        c (case type
                :line (LineChart. x y)
                :area (AreaChart. x y)
                :scatter (ScatterChart. x y)
                :bar (BarChart. x y)
                :stacked-bar (StackedBarChart. x y)
                :stacked-area (StackedAreaChart. x y))]
    (-> c .getData (set-all (map (fn [xy-data]
                                   (->> xy-data
                                        (transform-x-axis type)
                                        (xy-series {:name "Data" :flipped? flipped?})))
                                 xy-datas)))
    c))

(defn xy-chart-v
  [coll]
  (let [loader (FXMLLoader. (io/resource "cognitect/rebl/series-pair-chart.fxml"))
        root (.load loader)
        names (.getNamespace loader)
        node (fn [id] (.get names id))
        pane (node "chartView")
        chart (xy-chart {:type :bar} [coll])]
    (ui/update-pane pane chart)
    root))

(rebl/update-viewers {:rebl/xy-chart {:pred xy-chartable? :ctor xy-chart-v}})



