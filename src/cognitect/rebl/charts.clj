;;   Copyright (c) Cognitect, Inc. All rights reserved.

(ns cognitect.rebl.charts
  (:import [javafx.scene.chart AreaChart BarChart CategoryAxis LineChart NumberAxis ScatterChart XYChart XYChart$Data XYChart$Series])
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

(defn xy-series
  [name pairs]
  (let [ser (XYChart$Series.)]
    (.setName ser (fx/finite-pr-str name))
    (-> ser .getData (set-all (map xy-data pairs)))
    ser))

(defn xy-chart
  [{:keys [x-label y-label type] :or {x-label "x" y-label "y"}}
   colls]
  (let [x (NumberAxis.)
        y (NumberAxis.)
        c (case type
                :line (LineChart. x y)
                :area (AreaChart. x y)
                :scatter (ScatterChart. x y))]
    (.setLabel x x-label)
    (.setLabel y y-label)
    (-> c .getData (set-all (map (fn [[name data]]
                                   (xy-series name data))
                                 colls)))
    c))

(defn seq-numbers-chart
  [coll]
  (xy-chart {:x-label "x" :y-label "y" :type :line} [["Index" (map-indexed vector coll)]]))

(defn number-pairs-chart
  [coll]
  (xy-chart {:x-label "first" :y-label "second" :type :area} [["" coll]]))

(defn keyed-number-pairs-chart
  [coll]
  (xy-chart {:x-label "first" :y-label "second" :type :scatter} coll))

#_(defn coll-of-numbers-chart
  [coll]
  (histogram coll))

(rebl/update-viewers { ;; :charts/coll-of-numbers {:pred fx/coll-of-numbers? :ctor histogram}
                      :charts/seq-of-numbers {:pred fx/seq-numbers? :ctor seq-numbers-chart}
                      :charts/number-pairs {:pred fx/number-pairs? :ctor number-pairs-chart}
                      :charts/keyed-number-pairs {:pred fx/keyed-number-pairs? :ctor keyed-number-pairs-chart}})

