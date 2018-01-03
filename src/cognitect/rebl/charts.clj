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

(defn x-axis-for
  [chart]
  (if (get #{:bar :stacked-bar} chart)
    (CategoryAxis.)
    (NumberAxis.)))

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
                                   (xy-series name data))
                                 serieses)))
    c))

(defn series-pair-able?
  [x]
  (and (vector? x)
       (every? fx/number-pair? (fx/sample x fx/coll-check-limit))))

(defn chart-chosen
  [pane kw coll]
  (ui/update-pane pane (series-pair-chart {:type kw} [["Foo" coll]])))

(defn series-pair-chart-v
  [coll]
  (let [loader (FXMLLoader. (io/resource "cognitect/rebl/series-pair-chart.fxml"))
        root (.load loader)
        names (.getNamespace loader)
        node (fn [id] (.get names id))
        chart-choice (node "chartChoice")
        pane (node "chartView")]
    (ui/update-choice chart-choice [:area :line :scatter] :area)
    (-> chart-choice .valueProperty (.addListener (fx/change-listener (fn [ob ov nv] (chart-chosen pane nv coll)))))
    root))

(rebl/update-viewers {:charts/series-pair {:pred series-pair-able? :ctor series-pair-chart-v}})

#_(rebl/update-viewers { ;; :charts/coll-of-numbers {:pred fx/coll-of-numbers? :ctor histogram}
                      :charts/seq-of-numbers {:pred fx/seq-numbers? :ctor seq-numbers-chart}
                      :charts/number-pairs {:pred fx/number-pairs? :ctor number-pairs-chart}
                      :charts/keyed-number-pairs {:pred fx/keyed-number-pairs? :ctor keyed-number-pairs-chart}})

