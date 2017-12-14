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


