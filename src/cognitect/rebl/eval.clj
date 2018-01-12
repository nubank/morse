(ns cognitect.rebl.eval
  (:import [java.io PushbackReader ByteArrayInputStream]))

(set! *warn-on-reflection* true)

(defn read-form-string
  "Given a stream (java.io.PushbackReader or some derivee) reads and returns a form as string, skipping whitespace.
  Throws on parsing errors."
  [^PushbackReader stream]
  (let [eol #{\return \newline}
        delims {\( \) \{ \} \[ \]}
        ends #{\) \} \]}
        lit-terminals (into eol (concat (keys delims) [\" \\ \;]))
        new-buffer #(StringBuilder. 1024)]
    (loop [state :top, stack [] ^StringBuilder sb (new-buffer)]
      (let [c (.read stream)]
        (if (== -1 (int c))
          (throw (RuntimeException. "premature eos"))
          (let [c (char c)
                eol? (eol c)]
            (cond
             ;; discard top-level whitespace
             (and (= state :top) (Character/isWhitespace c)) (recur state stack sb)

             (= state :literal) (if (or (Character/isWhitespace c) (lit-terminals c))
                                  (do
                                    (.unread stream (int c))
                                    (.toString sb))
                                  (recur state stack (.append sb c)))

             (= state :comment) (recur (if eol? :top :comment) stack sb)

             (= c \\) (recur state stack sb)

             (= state :string) (let [sb (.append sb c)]
                                 (if (= c \")
                                   (if (empty? stack)
                                     (.toString sb)
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
                    (throw (RuntimeException. msg)))
                  (-> sb
                      (.append c)
                      (.toString))))

              (= \" c) (recur :string stack (.append sb c))

              (= \; c) (recur :comment stack sb)

              (= state :top)
              (recur :literal stack (.append sb c))

              :else
              (recur state stack (.append sb c))))))))))

(comment
(require '[cognitect.rebl.eval :as e])
(require '[clojure.java.io :as io])
(def ^String s "(+ 1 2)\nabc1\ndef")
(def is (java.io.ByteArrayInputStream. (.getBytes s)))
(def rdr (java.io.PushbackReader. (io/reader is)))

(e/read-form-string rdr)

(def ^String s "foo \"bar\" (\"baz\") \"woz\"")
(def is (java.io.ByteArrayInputStream. (.getBytes s)))
(def rdr (java.io.PushbackReader. (io/reader is)))

(e/read-form-string rdr)

)
