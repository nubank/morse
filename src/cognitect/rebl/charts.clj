;;   Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.charts
  (:import
   [javafx.collections ObservableList]
   [javafx.fxml FXMLLoader]
   [javafx.scene.chart AreaChart BarChart CategoryAxis LineChart NumberAxis ScatterChart StackedAreaChart StackedBarChart XYChart XYChart$Data XYChart$Series])
  (:require
   [clojure.java.io :as io]
   [cognitect.rebl :as rebl]
   [cognitect.rebl.fx :as fx]
   [cognitect.rebl.ui :as ui]))

(defn- set-all
  [^ObservableList container data]
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
  [{:keys [type x-range x-label]}]
  (doto (if (contains? {:bar :stacked-bar} type)
          (CategoryAxis.)
          (if x-range
            (NumberAxis.)  ;; TODO: this also would require tickunit
            (NumberAxis.)))
    (.setLabel x-label)))

(defn y-axis-for
  [{:keys [y-range y-label]}]
  (doto (if y-range
          (NumberAxis.)
          (NumberAxis.))
    (.setLabel y-label)))

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

(def xy-chart-defaults
  {:type :bar
   :title "Data"
   :x-label "X"
   :y-label "Y"})

(defn xy-chart
  [{:keys [title type flipped?] :as config} series]
  (let [xy-data (to-xy-series-data series)
        x (x-axis-for config)
        y (y-axis-for config)
        [x y] (if flipped? [y x] [x y])
        c (case type
                :line (LineChart. x y)
                :area (AreaChart. x y)
                :scatter (ScatterChart. x y)
                :bar (BarChart. x y)
                :stacked-bar (StackedBarChart. x y)
                :stacked-area (StackedAreaChart. x y))]
    (-> c .getData (set-all [(->> xy-data
                                  (transform-x-axis type)
                                  (xy-series {:name title :flipped? flipped?}))]))
    c))

(defn xy-chart-v
  [coll]
  (let [loader (FXMLLoader. (io/resource "cognitect/rebl/series-pair-chart.fxml"))
        root (.load loader)
        names (.getNamespace loader)
        node (fn [id] (.get names id))
        pane (node "chartView")
        config (merge xy-chart-defaults (-> coll meta :rebl/xy-chart))
        chart (xy-chart config coll)]
    (ui/update-pane pane chart)
    root))

(rebl/update-viewers {:rebl/xy-chart {:pred xy-chartable? :ctor xy-chart-v}})



