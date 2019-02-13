# `REBL`

## Read-Eval-Browse-Loop

REBL is a graphical, interactive tool for browsing Clojure data. It features:

* a two-pane browser/viewer system for viewing collections and their contents
* navigation into and back out of nested collections
* a structured editor pane for entering expressions to be evaluated
* a root browse of a history of expression evaluations
* the ability to 'wrap' a stdio based REPL (e.g. Clojure's native REPL) so that it can monitor and display the interactions therein, while still allowing them to flow back to the host (e.g. the editor)
* when used with non-stdio repls (e.g. nREPL), can be launched a la carte and accepts values to inspect via an API call
* the ability to capture nested values as defs in the user namespace
* the ability to run multiple independent UI windows
* metadata viewing
* datafy support
* extensibility to new browsers and viewers
* full keyboard control via [hotkeys](https://github.com/cognitect-labs/rebl/wiki/Hotkeys)

![screenshot](screenshot.png)

REBL runs in your application JVM process, and can be used at dev-time without adding any runtime deps. The UI is written in JavaFX.

## Requirements

- Clojure 1.10.0-RC2 or later
- Java 1.8.0_151 or higher
- core.async (tested on 0.4.490)

## Usage:

`git clone` this repo

add an alias to (your existing project's) deps.edn:

``` clj
{:deps {}
 :aliases
 {:rebl {:extra-deps {
        org.clojure/core.async {:mvn/version "0.4.490"}
	org.clojure/clojure {:mvn/version "1.10.0"}
	com.cognitect/rebl {:local/root "/Users/rich/dev/rebl"}}}}}
```

replace your normal repl invocation (`clj`, or `clojure` e.g. for inferior-lisp) with REBL:

`clj -R:rebl -m cognitect.rebl`

Your repl should start, along with the REBL UI. Everything you type in the repl will also appear in REBL. You can also type expressions right into REBL's editor (in the upper left). REBL will maintain a history of exprs+results in the root browse table.

You can start more UIs with `(cognitect.rebl/ui)`

You can also use REBL with [boot](https://github.com/cognitect-labs/rebl/wiki/Using-REBL-with-Boot) or [lein](https://github.com/cognitect-labs/rebl/wiki/Using-REBL-with-Leiningen).

## Releases

Public releases are sourced from `private-releases-1fc2183a` which is
a private bucket.

The REBL download page can create time limited S3 download links as
follows:

Read `s3://private-releases-1fc2183a/releases/REBL/manifest.edn`,
which contains a map describing the current release.

    {:current {:bucket "<bucket-name>"
               :key "<KEY>"}}

Create a temporary download link to that bucket and key, where the
text name of the link is the last path element,
e.g. "rebl-0.9.99.zip".

Prior to initial release, the manifest just points to a "coming soon"
text file, so test away. 


