# `Morse`

## Rationale

One of the prime value propositions in using a Lisp language is that you should be able to connect to and interact with your live program. Doing so allows you to access and explore the program state. The typical access mechanism for Clojure programs is the REPL, but its textual output is not ideal for robust data exploration. Instead, an interface that allows you to browse and navigate the data in your live program is a more ideal environment for understanding. Morse, like REBL before it, provides a browser for Clojure data that allows active traversal into and back out of nested forms. However, it's not always feasible to execute the browser process inside of the live program. Therefore, Morse provides a way to inspect the live program's data remotely, but also provides an in-process mode if needed.

This software is considered an alpha release and subject to change.

Morse runs remotely to or within your application JVM process, and can be used at dev-time without adding any runtime deps. The UI is written in JavaFX.

Quick links:

* [Getting Started with Clojure](https://clojure.org/guides/getting_started)
* [Morse Guide](docs/guide.adoc)
* [Replicant Server](https://github.com/clojure/data.alpha.replicant-server)
* [Ask Clojure Q&A forum](https://ask.clojure.org/index.php/tools/morse)

## Requirements

* Clojure, 1.10.0 or higher
* Java 11 or higher

## Release Information

Latest release:

[deps.edn](https://clojure.org/reference/deps_and_cli) dependency information:

Latest Morse Git dependency coordinate:

```clojure
io.github.nubank/morse {:git/tag "v2023.04.25.01" :git/sha "f7a719e"}
``` 
## Installing Morse as a Clojure CLI tool

Morse is available as a Clojure CLI tool and may be installed and upgraded via:

    clj -Ttools install-latest :lib io.github.nubank/morse :as morse

## Usage:

See the [Morse Guide](docs/guide.adoc) to learn how to use Morse for your purposes.

## Docs

* [API.md](docs/API.md)

## Contributing

Morse is open source, developed internally at Nubank. Issues can be filed using GitHub issues for this project. Initially, we prefer to do development internally and are not accepting pull requests or patches.

## Copyright and License

Copyright © 2023 Nu North America, Inc

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

