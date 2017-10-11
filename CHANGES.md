## Unreleased

* Update Java Sentry dependency to 1.5.4. This includes [getsentry/sentry-java#483](https://github.com/getsentry/sentry-java/pull/483) which introduces a max recursion depth of 3. Deeply nested data will be replaced with "<recursion limit hit>".
