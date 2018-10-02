;;   Copyright (c) Cognitect, Inc. All rights reserved

(ns cognitect.rebl.spi)

(defprotocol AsData
  (as-data [o] "return a representation of o as data
    (default identity,
     override with :cognitect.rebl.spi/as-data-fn in metadata, will be called with o)"))

(extend-protocol AsData
  nil
  (as-data [_]nil)

  Object
  (as-data [x] x))

