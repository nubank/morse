(ns cognitect.rebl.eval
  (:require
   [clojure.core.async :as async :refer [<! >!]])
  (:import
   [java.io ByteArrayInputStream]
   [clojure.lang LineNumberingPushbackReader]))

(set! *warn-on-reflection* true)

(defonce ^:private -evaluators (atom {}))

(defn evaluators [] (deref -evaluators))

(defn add-evaluator
  "An evaluator is a fn of {:keys [form ret-fn bindings]}
  :form will be a string to be read/evaled
  :bindings will be ignored when evaluator has own (e.g. remote)
  context. Evaluator should call ret-fn with the input map to which
  a :val, :start and :elapsed keys has been added and (possibly) :bindings updated. If the
  read or evaluation results in an exception, :val should be
  a Throwable->map representation thereof, and :ex can be the original exception
  if available"
  [identk efn]
  (swap! -evaluators assoc identk efn))

(defn read-form-string
  "Given a stream (clojure.lang.LineNumberingPushbackReader or some
  derivee) reads and returns a form as string.
  Throws on parsing errors."
  [^LineNumberingPushbackReader stream]
  (try
    (.captureString stream)
    (binding [*reader-resolver* (reify clojure.lang.LispReader$Resolver
                                     (currentNS [_] 'resolver)
                                     (resolveClass [_ sym] sym)
                                     (resolveAlias [_ sym] sym)
                                     (resolveVar [_ sym] sym))]
      (read stream))
    (.getString stream)
    (catch Throwable ex
      (.getString stream)
      (throw ex))))

(defn do-eval
  [{:keys [form ret-fn bindings] :as args}]
  (push-thread-bindings bindings)
  (try
    (let [args (assoc args :start (java.util.Date.))
          start (System/nanoTime)
          expr (read-string form)
          val (try (eval expr)
                   (catch Throwable ex ex))
          ret (assoc args :bindings (get-thread-bindings)
                     :elapsed (/ (double (- (System/nanoTime) start)) 1000000.0))
          ret (if (instance? Throwable val)
                (assoc ret :val (Throwable->map val) :ex val)
                (assoc ret :val val))]
      (ret-fn ret))
    (catch Throwable ex
      (ret-fn (assoc args :val (Throwable->map ex) :ex ex)))
    (finally (pop-thread-bindings))))

(defonce ^:private add-rebl-eval
  (add-evaluator
   :rebl
   (let [ch (async/chan 10)]
     (async/go-loop [args (<! ch)]
                    (when (some? args)
                      (future (do-eval args))
                      (recur (<! ch))))
     (fn [args]
       (async/put! ch args)))))

