(ns cognitect.rebl.eval
  (:require [clojure.core.async :as async :refer [>! <! chan close!]])
  (:import [java.io PushbackReader ByteArrayInputStream]))

(defn stream->str-chan
  "Given a stream (java.io.PushbackReader or some derivee) return a
   channel of forms as strings.

   Opts is a persistent map with valid keys:
    :eof - on eof, return value unless  then throw.
           if not specified, will throw"
  ([stream]
   (read {:eof-value -1} stream))
  ([{:keys [eof-value] :or {eof-value -1}} ^PushbackReader stream]
   (let [ch (chan 1)
         eol #{\return \newline}
         delims {\( \) \{ \} \[ \]}
         ends #{\) \} \]}
         lit-terminals (into eol (concat (keys delims) [\" \\ \;]))
         new-buffer #(StringBuilder. 1024)]
     (async/go
       (loop [state :top, stack [] sb (new-buffer)]
         (let [c (.read stream)]
           (if (== -1 (int c))
             (do
               (when (pos? (.length sb))
                 (>! ch (.toString sb)))
               (>! ch eof-value)
               (close! ch))
             (let [c (char c)
                   eol? (eol c)]
               (cond
                 ;; discard top-level whitespace
                 (and (= state :top) (Character/isWhitespace c)) (recur state stack sb)

                 (= state :literal) (if (or (Character/isWhitespace c) (lit-terminals c))
                                      (do
                                        (when-not (Character/isWhitespace c)
                                          (.push c stream))
                                        (>! ch (.toString sb))
                                        (recur :top stack (new-buffer)))
                                      (recur state stack (.append sb c)))

                 (= state :comment) (recur (if eol? :top :comment) stack sb)

                 (= c \\) (recur state stack sb)

                 (= state :string) (let [sb (.append sb c)]
                                     (if (= c \")
                                       (if (empty? stack)
                                         (do
                                           (>! ch (.toString sb))
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
                       (do
                         (>! ch (ex-info (str "Unmatched delimiter - " (.toString sb))))
                         (close! ch))
                       (do
                         (-> sb
                           (.append c)
                           (.toString))
                         (let [stack' (pop stack)]
                           (>! ch (.toString sb))
                           (recur (if (empty? stack') :top :sexpr)
                             stack' (new-buffer))))))

                   (= \" c) (recur :string stack (.append sb c))

                   (= \; c) (recur :comment stack sb)

                   (= state :top)
                   (recur :literal stack (.append sb c))

                   :else
                   (recur state stack (.append sb c)))))))))
     ch)))

(comment

  (def s "(+ 1 2)\nabc1\ndef")
  (def is (ByteArrayInputStream. (.getBytes s)))
  (def rdr (PushbackReader. (io/reader is)))

  (def c (stream->str-chan {:eof-value :eof} rdr))

  (<!! c)

  (def s "foo \"bar\" (\"baz\") \"woz\"")
  (def is (ByteArrayInputStream. (.getBytes s)))
  (def rdr (PushbackReader. (io/reader is)))

  (def c (stream->str-chan {:eof-value :eof} rdr))

  (<!! c)

  )
