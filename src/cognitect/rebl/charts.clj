;;   Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.charts
  (:import [javafx.scene.chart BarChart CategoryAxis LineChart NumberAxis XYChart XYChart$Data XYChart$Series])
  (:require
   [cognitect.rebl :as rebl]
   [cognitect.rebl.fx :as fx]))

(defn- set-all
  [container data]
  (.setAll container (fx/fxlist data)))

(defn- xy-data
  [[x y]]
  (XYChart$Data. x y))

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

(defn line-chart-series
  [name pairs]
  (let [ser (XYChart$Series.)]
    (.setName ser name)
    (-> ser .getData (set-all (map xy-data pairs)))
    ser))

(defn line-chart
  [coll]
  (let [x (NumberAxis.)
        y (NumberAxis.)
        lc (LineChart. x y)]
    (.setLabel x "x")
    (.setLabel x "y")
    (-> lc .getData (set-all [(line-chart-series "val=fn(idx)" (map-indexed vector coll))]))
    lc))

(defn coll-of-numbers-chart
  [coll]
  (histogram coll))

(rebl/update-viewers {:charts/coll-of-numbers {:pred fx/coll-of-numbers? :ctor histogram}
                      :charts/seq-of-numbers {:pred fx/seq-of-numbers? :ctor line-chart}})

