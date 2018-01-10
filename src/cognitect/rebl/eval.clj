(ns cognitect.rebl.eval
  (:import [java.io PushbackReader ByteArrayInputStream]))

(set! *warn-on-reflection* true)

(defn form-strings
  "Given a stream (java.io.PushbackReader or some derivee) calling on-form with each form string.
  Calls on-eos (when supplied) on end-of-stream, discarding any incomplete form in
  progress. Call on-err (when supplied) with a string message on parsing errors."
  [^PushbackReader stream on-form & {:keys [on-eos on-err]}]
  (let [eol #{\return \newline}
        delims {\( \) \{ \} \[ \]}
        ends #{\) \} \]}
        lit-terminals (into eol (concat (keys delims) [\" \\ \;]))
        new-buffer #(StringBuilder. 1024)]
    (future
     (loop [state :top, stack [] ^StringBuilder sb (new-buffer)]
       (let [c (.read stream)]
         (if (== -1 (int c))
           (when on-eos
             (on-eos))
           (let [c (char c)
                 eol? (eol c)]
             (cond
              ;; discard top-level whitespace
              (and (= state :top) (Character/isWhitespace c)) (recur state stack sb)

              (= state :literal) (if (or (Character/isWhitespace c) (lit-terminals c))
                                   (do
                                     (when-not (Character/isWhitespace c)
                                       (.unread stream (int c)))
                                     (on-form (.toString sb))
                                     (recur :top stack (new-buffer)))
                                   (recur state stack (.append sb c)))

              (= state :comment) (recur (if eol? :top :comment) stack sb)

              (= c \\) (recur state stack sb)

              (= state :string) (let [sb (.append sb c)]
                                  (if (= c \")
                                    (if (empty? stack)
                                      (do
                                        (on-form (.toString sb))
                                        (recur :top stack (new-buffer)))
                                      (recur :sexpr stack sb))
                                    (recur state stack sb)))

              :else
              (cond
               (delims c)
               (recur :sexpr (conj stack {:char c}) (.append sb c))
               
               (ends c)
               (let [d (peek stack)]
                 (if (or (nil? d) (not= c (delims (:char d))))
                   ;;unmatched delim, close?
                   (let [msg (str "unmatched delimiter: " d)]
                     (when on-err
                       (on-err msg))
                     (recur :top [] (new-buffer)))
                   (do
                     (-> sb
                         (.append c)
                         (.toString))
                     (let [stack' (pop stack)]
                       (on-form (.toString sb))
                       (recur (if (empty? stack') :top :sexpr)
                              stack' (new-buffer))))))

               (= \" c) (recur :string stack (.append sb c))

               (= \; c) (recur :comment stack sb)

               (= state :top)
               (recur :literal stack (.append sb c))

               :else
               (recur state stack (.append sb c)))))))))
    nil))

(comment
(require '[cognitect.rebl.eval :as e])
(require '[clojure.java.io :as io])
(def ^String s "(+ 1 2)\nabc1\ndef")
(def is (java.io.ByteArrayInputStream. (.getBytes s)))
(def rdr (java.io.PushbackReader. (io/reader is)))

(def c (e/form-strings rdr prn))

(def ^String s "foo \"bar\" (\"baz\") \"woz\"")
(def is (java.io.ByteArrayInputStream. (.getBytes s)))
(def rdr (java.io.PushbackReader. (io/reader is)))

(def c (e/form-strings rdr prn))

)
