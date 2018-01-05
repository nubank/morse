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
  [{:keys [convx convy flipped?]} [x y extra :as v]]
  (let [x ((or convx identity) x)
        y ((or convy identity) y)
        [x y] (if flipped? [y x] [x y])]
    (case (count v)
          2 (XYChart$Data. x y)
          3 (XYChart$Data. y x extra))))

;; TODO: update to current API and make live
#_(defn histogram
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
  [{:keys [name] :as opts} tuples]
  (let [ser (XYChart$Series.)]
    (.setName ser (fx/finite-str name))
    (-> ser .getData (set-all (map #(xy-data opts %) tuples)))
    ser))

;; TODO: infer from data, not from chart type
(defn x-axis-for
  [chart]
  (if (get #{:bar :stacked-bar} chart)
    (CategoryAxis.)
    (NumberAxis.)))

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

;; TODO  :coll-of-categories :pair-of-colls :triple-of-colls :coll-of-triples
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
  [{:keys [x-label y-label type flipped?] :or {x-label "x" y-label "y"}}
   serieses]
  (let [x (doto (x-axis-for type) (.setLabel x-label))
        y (doto (NumberAxis.) (.setLabel y-label))
        [x y] (if flipped? [y x] [x y])
        c (case type
                :line (LineChart. x y)
                :area (AreaChart. x y)
                :scatter (ScatterChart. x y)
                :bar (BarChart. x y)
                :stacked-bar (StackedBarChart. x y)
                :stacked-area (StackedAreaChart. x y))]
    (-> c .getData (set-all (map (fn [[name data]]
                                   (to-xy-series-data {:name name :flipped? flipped?} data))
                                 serieses)))
    c))

(defn chart-changed
  [node coll]
  (let [type (.getValue (node "chartChoice"))
        flipped? (.isSelected (node "flippedButton"))
        chart (series-pair-chart {:type type
                                  :flipped? flipped?}
                                 [["Data" coll]])]
    (ui/update-pane (node "chartView") chart)))

(defn series-pair-chart-v
  [coll]
  (let [loader (FXMLLoader. (io/resource "cognitect/rebl/series-pair-chart.fxml"))
        root (.load loader)
        names (.getNamespace loader)
        node (fn [id] (.get names id))
        chart-choice (node "chartChoice")
        flipped-button (node "flippedButton")
        pane (node "chartView")
        changed (fx/change-listener (fn [_ _ _] (chart-changed node coll)))]
    (-> chart-choice .valueProperty (.addListener changed))
    (-> flipped-button .selectedProperty (.addListener changed))
    (ui/update-choice chart-choice [:area :line :scatter] :scatter)
    root))

(rebl/update-viewers {:charts/series-pair {:pred xy-chartable? :ctor series-pair-chart-v}})



