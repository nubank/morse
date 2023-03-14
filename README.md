# `Morse`

## Rationale

One of the prime value propositions in using a Lisp language is that you should be able to connect to and interact with your live program. Doing so allows you to access and explore the program state. The typical access mechanism for Clojure programs is the REPL, but its textual output is not ideal for robust data exploration. Instead, an interface that allows you to browse and navigate the data in your live program is a more ideal environment for understanding. Morse, like REBL before it, provides a browser for Clojure data that allows active traversal into and back out of nested forms. However, it's not always feasible to execute the browser process inside of the live program. Therefore, Morse provides a way to inspect the live program's data remotely, but also provides an in-process mode if needed.

## The Morse UI

Morse is a graphical, interactive tool for browsing Clojure data. It features:

* In-process and remote inspection modes
* a two-pane browser/viewer system for viewing collections and their contents
* navigation into and back out of nested collections
* a structured editor pane for entering expressions to be evaluated
* a root browse of a history of expression evaluations
* when used with non-stdio repls (e.g. nREPL), can be launched a la carte and accepts values to inspect via an API call
* the ability to capture nested values as defs in the user namespace
* metadata viewing
* datafy support
* full keyboard control via [hotkeys](https://github.com/cognitect-labs/rebl/wiki/Hotkeys)

![screenshot](TODO)

Morse runs remotely to or within your application JVM process, and can be used at dev-time without adding any runtime deps. The UI is written in JavaFX.

## Requirements

* Clojure, 1.10.0 or higher
* Java
  * Java 8 1.8.0_151 or higher, with embedded JavaFX or OpenJavaFX
    * Distributions include: Oracle, Azul, BellSoft Liberica, Amazon Coretto
    * AdoptOpenJDK DOES NOT include OpenJavaFX and is not supported
      * Either use Java 11+ or see these [suggestions](https://github.com/AdoptOpenJDK/openjdk-build/issues/577#issuecomment-557496591) (unupported)
  * Java 11 or higher with external OpenJavaFX (see below)

## Release Information

Latest release:

[deps.edn](https://clojure.org/reference/deps_and_cli) dependency information:

As a maven dep:

```clojure
{:aliases
 {:morse        ;; for JDK 11+
  {:extra-deps {com.cognitect/morse         {:mvn/version "TODO"}
                org.openjfx/javafx-fxml     {:mvn/version "15-ea+6"}
                org.openjfx/javafx-controls {:mvn/version "15-ea+6"}
                org.openjfx/javafx-swing    {:mvn/version "15-ea+6"}
                org.openjfx/javafx-base     {:mvn/version "15-ea+6"}
                org.openjfx/javafx-web      {:mvn/version "15-ea+6"}}
   :main-opts ["-m" "cognitect.morse"]}
  :morse-jdk8   ;; for JDK 8
  {:extra-deps {com.cognitect/morse {:mvn/version "TODO"}}
   :main-opts ["-m" "cognitect.morse"]}}}
```

## Usage:

Morse requires two parts to operate. First, a server component [replicant-server](https://github.com/clojure/replicant-server) should run in the process that you wish to inspect. Second, Morse itself will act as a client to an active Replicant. To launch Morse run the following command:

    clj -X:morse

If using JDK-8:

    clj -X:morse-jdk8

Once you connect, the REPL pane in Morse is a remote client of the server (via a socket) of the server you started above. Expressions you type there are evaluated in the replicant server process. This is just like any remote socket-based repl.

You can also type expressions right into Morse's editor (in the upper left) which will evaluate in the remote process. Morse will maintain a history of exprs+results in the root browse table.


