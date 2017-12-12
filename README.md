# `REBL`

## Read-Eval-Browse-Loop

REBL ...

## Requirements

- Clojure 1.9
- Java 1.8.0_151 or higher

## Usage:

`git clone` this repo

add an alias to your deps.edn:

``` clj
{:deps {}
 :aliases
 {:rebl {:extra-deps {com.cognitect/rebl {:local/root "/Users/rich/dev/rebl"}}}}}
```

replace your normal repl invocation (e.g. for inferior-lisp) with rebl:

`clojure -R:rebl -m cognitect.rebl`

You should see the UI while still getting repl evals in your editor.

You can start more UIs with `(cognitect.rebl/ui)`

## Viewers

A viewer gives REBL the ability to render data. A viewer (definition?)
comprises three things:

* a qualified keyword (`identk`) that is a globally unique name for
  the viewer
* a predicate (`pred`) that returns truthy for any data that the
  viewer supports
* a 1-arg constructor function (`ctor`) that, given data matching
  the predicate, creates a JavaFX Node.

(Proposed)
You can add new viewers to REBL at any time with

    (rebl/add-viewer new-viewers)

where `new-viewers` is a map `identk` -> `{:keys [pred ctor]}`

Specifying `nil` as a value for an `identk` removes the viewer for
that key.

## Browsers

A browser can render data (like a viewer), but it also supports
navigation to subelements. A browser (definition?) comprises three
things:

* a qualified keyword (`identk`) that is a globally unique name for
  the browser
* a predicate (`pred`) that returns truthy for any data that the
  browser supports
* a 2-arg constructor function (`ctor`) that, given data matching the
  predicate and a selection callback function, creates a JavaFX Node.

The selection callback function is a function of two arguments:

* the "position" (index or key) of the selected item
* the selected item itself

The browser constructor should add a selection listener for its
subcomponents that calls the selection callback as described above.

Note that because viewer and browser constructors have different
arities, it is idiomatic to use a multi-arity function to define both
a viewer and a browser for the same data.

(Proposed)
You can add new browsers to REBL at any time with

    (rebl/add-browser new-browsers)

where `new-browsers` is a map `identk` -> `{:keys [pred ctor]}`

Specifying `nil` as a value for an `identk` removes the browser for
that key.

## identk Namespaces

You should name viewers and browsers using namespaces that you
control, i.e. the same reversed-domain-name prefix used to name Java
packages. All namespaces beginning with `rebl` are reserved for REBL's
use.


