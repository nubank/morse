;;   Copyright (c) Cognitect, Inc. All rights reserved

(ns cognitect.rebl.data
  (:require
   [cognitect.rebl.spi :as spi]
   [clojure.reflect :as refl]))

(set! *warn-on-reflection* true)

(defn nav [coll x]
  (if-let [nf (-> coll meta ::spi/nav-fn)]
    (nf x)
    x))

(defn as-data [x]
  (let [v ((or (-> x meta ::spi/as-data-fn) spi/as-data) x)]
    (if (identical? v x)
      v
      (with-meta v (merge {:rebl/obj x :java/class (class x)} (meta x))))))

(defn iref-as-data [r]
  (with-meta [(deref r)] (meta r)))

(defn sortmap [m]
  (into (sorted-map) m))

(defn ns-as-data [n]
  (with-meta {:publics (-> n ns-publics sortmap)
              :imports (-> n ns-imports sortmap)
              :interns (-> n ns-interns sortmap)}
    (meta n)))

(defn class-as-data [c]
  (let [{:keys [members] :as ret} (refl/reflect c)]
    (assoc ret :members (->> members (group-by :name) sortmap))))

(extend-protocol spi/AsData
  clojure.lang.IRef
  (as-data [r] (iref-as-data r))

  clojure.lang.Namespace
  (as-data [n] (ns-as-data n))

  java.lang.Class
  (as-data [c] (class-as-data c)))

