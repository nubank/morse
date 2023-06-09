;;   Copyright (c) Nu North America, Inc. All rights reserved.

(ns dev.nu.morse.charts.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::title string?)
(s/def ::x-label string?)
(s/def ::y-label string?)
(s/def ::x-range (s/tuple double? double?))
(s/def ::y-range (s/tuple double? double?))
(s/def ::type #{:line :area :scatter :bar})

(s/def :rebl/xy-chart (s/keys :opt-un [::title ::type ::x-label ::y-label
                                       ::x-range ::y-range]))

