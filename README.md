# raven-clj

A thin wrapper around the
[official Java library for Sentry](https://github.com/getsentry/raven-java/).

## Usage

```clojure
(require '[raven-clj.core :as raven])

(def dsn
  "https://blah:blee@sentry.io/bloo")

(try
  (do-something-risky)
  (catch Exception e
    (raven/send-event dsn {:throwable e})))
```

## License

Copyright © 2016 Coda Hale

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
