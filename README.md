# `Morse`

## Rationale

One of the prime value propositions in using a Lisp language is that you should be able to connect to and interact with your live program. Doing so allows you to access and explore the program state. The typical access mechanism for Clojure programs is the REPL, but its textual output is not ideal for robust data exploration. Instead, an interface that allows you to browse and navigate the data in your live program is a more ideal environment for understanding. Morse, like REBL before it, provides a browser for Clojure data that allows active traversal into and back out of nested forms. However, it's not always feasible to execute the browser process inside of the live program. Therefore, Morse provides a way to inspect the live program's data remotely, but also provides an in-process mode if needed.

## Building Morse

    clj -T:build uber

## Running Morse

Run with the Clojure CLI:

    clj -X:morse <options-map>

The `<options-map>` is optional but if omitted defaults to `'{:host \"localhost\", :port 5555}'`.

