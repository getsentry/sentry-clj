# Change Log

## Upcoming

* 3.1.next
  * In progress.

## Stable Builds

* 3.1.126 -- 2020-10-16
  * Move to the major.minor.commit model.
  * Remove unused library `clojure.java-time`.
  * Update pom.xml with some additional metadata information.
  * Added in a simple deploy script.

* 3.1.0 -- 2020-10-16
  * Major release bringing compatibility with version 3.1.0 of the Java Sentry
    library. This is a **BREAKING** change, insofar that this has only been
    tested with the latest Sentry (20.9.0) and is **not** compatible with
    Sentry 10.0.1 and below. If you wish to use those versions, please
    continue to use sentry-clj 1.7.30.
  * Added in the ability to define the Environment on configuration of Sentry
    (and not only per-event!)
  * Added in the ability to disable the built-in UncaughtExceptionHandler in
    order to allow defining our own.
  * Added in a couple of examples:
    * A basic example showing how to register a sentry-logger and how to fire
      an event.
    * An example that registers it's own uncaught exception handler to not
      only log out to sentry, but also to log out to a pre-configured logger.
