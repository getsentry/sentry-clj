<p align="center">
  <a href="https://sentry.io" target="_blank" align="center">
    <img src="https://sentry-brand.storage.googleapis.com/sentry-logo-black.png" width="280">
  </a>
  <br />
</p>

_Bad software is everywhere, and we're tired of it. Sentry is on a mission to help developers write better software faster, so we can get back to enjoying technology. If you want to join us [<kbd>**Check out our open positions**</kbd>](https://sentry.io/careers/)_

Sentry SDK for Clojure
===========

[![Clojars Project](https://img.shields.io/clojars/v/io.sentry/sentry-clj.svg)](https://clojars.org/io.sentry/sentry-clj)

A very thin wrapper around the [official Java library for
Sentry](https://docs.sentry.io/platforms/java/).

This project follows the version scheme MAJOR.MINOR.COMMITS where MAJOR and
MINOR provide some relative indication of the size of the change, but do not
follow semantic versioning. In general, all changes endeavour to be
non-breaking (by moving to new names rather than by breaking existing names).
COMMITS is an ever-increasing counter of commits since the beginning of this
repository.

## Usage

```clojure
(require '[sentry-clj.core :as sentry])

; You can initialize Sentry manually by providing a DSN or use one of the
; other optional configuration options supplied as a map (see below).

(sentry/init! "https://public:private@sentry.io/1")

(try
  (do-something-risky)
  (catch Exception e
    (sentry/send-event {:message {:message "Something has gone wrong!"}
                        :throwable e})))
```

## Additional Initialisation Options

| key                                  | description                                                                                                        | default
| ------------------------------------ | ------------------------------------------------------------------------------------------------------------------ | -------
| `:environment`                       | Set the environment on which Sentry events will be logged, e.g., "staging"                                         | production
| `:debug`                             | Enable SDK logging at the debug level                                                                              | false
| `:release`                           | All events are assigned to a particular release                                                                    |
| `:shutdown-timeout`                  | Wait up to X milliseconds before shutdown if there are events to send                                              | 2000ms
| `:in-app-excludes`                   | A seqable collection (vector for example) containing package names to ignore when sending events                   |
| `:enable-uncaught-exception-handler` | Enables the uncaught exception handler                                                                             | true
| `:before-send-fn`                    | A function (taking an event and a hint)                                                                            |
|                                      | The body of the function must not be lazy (i.e., don't use filter on its own!) and must return an event or nil     |
|                                      | If a nil is returned, the event will not be sent to Sentry                                                         |
|                                      | [More Information](https://docs.sentry.io/platforms/java/data-management/sensitive-data/)                          |
| `:before-breadcrumb-fn`              | A function (taking a breadcrumb and a hint)                                                                        |
|                                      | The body of the function must not be lazy (i.e., don't use filter on its own!) and must return a breadcrumb or nil |
|                                      | If a nil is returned, the breadcrumb will not be sent to Sentry                                                    |
|                                      | [More Information](https://docs.sentry.io/platforms/java/enriching-events/breadcrumbs/)                            |
| `:contexts`                          | A map of key/value pairs to attach to every Event that is sent.                                                    |
|                                      | [More Information](https://docs.sentry.io/platforms/java/enriching-events/context/)                                |

Some examples:

Basic Initialisation (using defaults):

```clojure
(sentry/init! "https://public:private@sentry.io/1")
```

Initialisation with additional options:

```clojure
(sentry/init! "https://public:private@sentry.io/1" {:environment "staging" :debug true :release "foo.bar@1.0.0" :in-app-excludes ["foo.bar"])
```

```clojure
(sentry/init! "https://public:private@sentry.io/1" {:before-send-fn (fn [event _] (when-not (= (.. event getMessage getMessage "foo")) event))})
```

```clojure
(sentry/init! "https://public:private@sentry.io/1" {:before-send-fn (fn [event _] (.setServerName event "fred") event)})
```

```clojure
(sentry/init! "https://public:private@sentry.io/1" {:contexts {:foo "bar" :baz "wibble"}})
```

## Supported event keys

[API Documentation](https://develop.sentry.dev/sdk/event-payloads/)

- `:breadcrumbs` - a collection of `Breadcrumb` maps. See below.
- `:dist` - a `String` which identifies the distribution.
- `:environment` - a `String` which identifies the environment.
- `:event-id` - a `String` id to use for the event. If not provided, one will be automatically generated.
- `:extra` - a map with `Keyword` or `String` keys (or anything for which `clojure.core/name` can be invoked) and values which can be JSON-ified. If `:throwable` is given, this will automatically include its `ex-data`.
  - **note**: `:extra` has been deprecated in favour of `:contexts` upon initialisation
- `:fingerprint` - a sequence of `String`s that Sentry should use as a [fingerprint](https://docs.sentry.io/learn/rollups/#customize-grouping-with-fingerprints).
- `:level` - a `Keyword`. One of `:debug`, `:info`, `:warning`, `:error`, `:fatal`. Probably most useful in conjunction with `:message` if you need to report an exceptional condition that's not an exception.
- `:logger` - a `String` which identifies the logger.
- `:message` - a map containing Message information. See below.
- `:platform` - a `String` which identifies the platform.
- `:release` - a `String` which identifies the release.
- `:request` - a map containing Request information. See below.
- `:server-name` - a `String` which identifies the server name.
- `:tags` - a map with `Keyword` or `String` keys (or anything for which `clojure.core/name` can be invoked) and values which can be coerced to `Strings` with `clojure.core/str`.
- `:throwable` - a `Throwable` object. Sentry's bread and butter.
- `:transaction` - a `String` which identifies the transaction.
- `:user` - a map containing User information. See below.

### Breadcrumbs

[API Documentation](https://develop.sentry.dev/sdk/event-payloads/breadcrumbs/)

When an event has a `:breadcrumbs` key, each element of the value collection
should be a map. Each key is optional.

- `:type` - A `String`
- `:level` - a `String`
- `:message` - a `String`
- `:category` - a `String`
- `:data` - a map with `String` keys and `String` values

### Message

[API Documentation](https://develop.sentry.dev/sdk/event-payloads/message/)

When an event has a `:message` key, the following data should be contained
within a map, thus:

- `:formatted` - A `String` containing the fully formatted message. If missing, Sentry will try to interpolate the `message`.
- `:message` - An optional `String` containing the raw message. If there are params, it will be interpolated.
- `:params` - An optional sequence of `String`'s containing parameters for interpolation, e.g., `["foo" "bar"]

### User

[API Documentation](https://develop.sentry.dev/sdk/event-payloads/user/)

When an event has a `:user` key, the data following should be contained within
a map. You should provide at either the id or the ip-address.

- `:email` - A `String`
- `:id` - A `String`
- `:username` - A `String`
- `:ip-address` - A `String`
- `:other` - A map containing key/value pairs of `Strings`, i.e., `{"a" "b" "c" "d"}`

### Request

[API Documentation](https://develop.sentry.dev/sdk/event-payloads/request/)

When an event has a `:request` key, the data should be contained within a map.
Each key is optional.

- `:url` - A `String`
- `:method` - A `String`
- `:query-string` - A `String`
- `:data` - An arbitrary value (e.g., a number, a string, a blob...)
- `:cookies` - A `String`
- `:headers` - A map containing key/value pairs of `Strings`, i.e., `{"a" "b" "c" "d"}`
- `:env` - A map containing key/value pairs of `Strings`, i.e., `{"a" "b" "c" "d"}`
- `:other` - A map containing key/value pairs of `Strings`, i.e., `{"a" "b" "c" "d"}`

## License

Copyright © 2021 Coda Hale, Sentry

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
