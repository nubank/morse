{:web
 {:game-night (java.net.URL. "https://github.com/clojureconj/clojureconj2018/wiki/Board-Game-Night")}
 
 :data
 {:scalar "Hello World"
  :powers [0 1 4 9 16 25 36]
  :pairs [[1 2] [-3 5]]
  :keyed-pairs {:a [[1 2] [-3 5]] :b [[4 8]]}
  :tuples [[1 2] [3 4] [5 6]]
  :code '(defn foo [x] "Hello World")
  :bigger (repeatedly 100 (fn [] {:alpha (rand-int 100)
                                  :beta (rand-int 100)}))
  :chart (with-meta [1 2 3] {:rebl/xy-chart {:title "My Stuff"}})}
 
 :datafy
 {:throwable (ex-info "Boom" {:a 1 :b 2})
  :ref (atom (range 100))
  :spec (clojure.spec.alpha/keys :req-un [::a ::b])}
 
 :recursive-datafy
 {:class clojure.lang.APersistentVector
  :namespace (find-ns 'clojure.set)}}
