# dev.nu.morse 

## `eval`
``` clojure

(eval expr)
```


Sends the expr to Morse for evaluation. Returns if the send was successful.
<br><sub>[source](https://github.com/nubank/morse/blob/master/src/dev/nu/morse.clj#L100-L103)</sub>
## `inspect`
``` clojure

(inspect expr)
```


Macro.


Sends the expr and its value to the Morse UI.
<br><sub>[source](https://github.com/nubank/morse/blob/master/src/dev/nu/morse.clj#L93-L98)</sub>

## `launch-in-proc`
``` clojure

(launch-in-proc)
```


Launches an in-process mode Morse UI instance. The editor pane of that instance will
  use the hosting process to evaluate its contents.
<br><sub>[source](https://github.com/nubank/morse/blob/master/src/dev/nu/morse.clj#L146-L150)</sub>
## `launch-remote`
``` clojure

(launch-remote & {:keys [host port], :or {host "localhost", port 5555}})
```


Launches an remote mode Morse UI instance. The editor pane of that instance will
  use the remote process to evaluate its contents. This function accepts keyword args
  with mappings :host -> host-string and :port -> port-number. By default these values
  will map to :host -> "localhost" and :port -> 5555.
<br><sub>[source](https://github.com/nubank/morse/blob/master/src/dev/nu/morse.clj#L152-L160)</sub>
## `load-file`
``` clojure

(load-file filename)
```


Takes a filename and attempts to send each Clojure form found in it
  to Morse for evaluation.
<br><sub>[source](https://github.com/nubank/morse/blob/master/src/dev/nu/morse.clj#L105-L116)</sub>
## `morse`
``` clojure

(morse {:keys [host port mode], :or {port 5555, mode :remote}})
```


Launches a Morse UI instance. The editor pane of that instance will use the relevant
  (either in-proc or remote) process to evaluate its contents. This function accepts an opts map
  with mappings :host -> host-string, :port -> port-number, and :mode -> :remote | :in-proc.
  By default these values will map to :port -> 5555, and :mode -> :remote.
<br><sub>[source](https://github.com/nubank/morse/blob/master/src/dev/nu/morse.clj#L162-L170)</sub>

