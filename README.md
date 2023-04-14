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
* full keyboard control via [hotkeys](https://www.clojure.org/guides/morse#keybindings)

Morse runs remotely to or within your application JVM process, and can be used at dev-time without adding any runtime deps. The UI is written in JavaFX.

Quick links:

* [Getting Started with Clojure](https://clojure.org/guides/getting_started)
* [Morse Guide](https://clojure.org/guides/morse)
* [Replicant Server](https://github.com/clojure/data.alpha.replicant-server)

## Requirements

* Clojure, 1.10.0 or higher
* Java 11 or higher

## Release Information

Latest release:

[deps.edn](https://clojure.org/reference/deps_and_cli) dependency information:

Latest Morse Git dependency coordinate:

```clojure
io.github.nubank/morse {:git/tag "vTODO" :git/sha "TODO"}
``` 

## Usage:

Morse is available as a Clojure CLI tool and may be installed and upgraded via:

    clj -Ttools install-latest :lib io.github.nubank/morse-distro :as morse

That command installs a tool named "morse" that you can launch via:

    clj -Tmorse morse <options-map>

The `<options-map>` is optional but if omitted defaults to `'{:host \"localhost\", :port 5555}'`.

See the [Morse Guide](https://clojure.org/guides/morse#usage) for more details about using Morse.

## Copyright and License

Copyright © 2023 Nu North America, Inc

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

