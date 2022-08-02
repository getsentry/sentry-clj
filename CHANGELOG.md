# Change Log

All notable changes to this project will be documented in this file
and is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

This project follows the version scheme MAJOR.MINOR.COMMITS where
MAJOR and MINOR provide some relative indication of the size of the
change, but do not follow semantic versioning. In general, all changes
endeavour to be non-breaking (by moving to new names rather than by
breaking existing names). COMMITS is an ever-increasing counter of
commits since the beginning of this repository.

## [6.3.181]

- Update Sentry Java SDK to 6.3.1.
- shutdown-timeout renamed to shutdown-timeout-millis in keeping with the Java SDK changes
- added `serialization-max-depth` to work around circular reference errors when performing serialization of throwables
  - defaults to 5, although you can adjust lower if a circular reference loop occurs

## [5.7.180]

- Update Sentry Java SDK to 5.7.4.
- Upgrade Clojure to 1.11.1.

## [5.7.178]

- Update Sentry Java SDK to 5.7.3.

## [5.7.177]

- Update Sentry Java SDK to 5.7.2.

## [5.7.176]

### Added

- Various bugfixes, please see commit messages. Big thanks to @karuta0825 for these!
- Small typo fix.

## [5.7.172]

### Added

- The ability to use tracing with Sentry. Big thanks to @karuta0825. There is an example in the `examples` directory.

### Changed

- Update Sentry Java SDK to 5.7.1.
- Various library updates.
- Update github workers cache to v3

## [5.7.171]

### Changed

- Update Sentry Java SDK to 5.7.0.

## [5.6.170]

### Changed

- Fixed an issue where sometimes keywords where not being converted to strings #36
- Bumped various libraries

## [5.6.169]

### Changed

- Update Sentry Java SDK to 5.6.1.

## [5.6.166]

### Changed

- Update Sentry Java SDK to 5.6.0.

## [5.5.165]

### Changed

- Update Sentry Java SDK to 5.5.3.
- Deprecated `enable-uncaught-exception-handler` and added `uncaught-handler-enabled` to improve naming consistency with Java SDK.
- A little bit of code refactoring.

### Added

- Added `in-app-includes` to complement `in-app-excludes`.
- Added `ignored-exceptions-for-type` that accepts a vector of Classnames (as Strings) for Sentry to ignore.
- Added `dist` and `server-name` to the Sentry Options configuration.
- More tests.

## [5.5.164]

### Changed

- Update Sentry Java SDK to 5.5.2

## [5.5.163]

### Changed

- Update Sentry Java SDK to 5.5.1

## [5.5.162]

### Changed

- Update Sentry Java SDK to 5.5.0

## [5.4.161]

### Changed

- Fix build script

## [5.4.160]

### Changed

- Update Sentry Java SDK to 5.4.3

## [5.3.159]

### Changed

- Update Sentry Java SDK to 5.3.0

## [5.2.158]

### Changed

- Update Sentry Java SDK to 5.2.4

## [5.2.157]

### Changed

- Update Sentry Java SDK to 5.2.3

## [5.2.156]

### Changed

- Update Sentry Java SDK to 5.2.2

## [5.2.155]

### Changed

- Update Sentry Java SDK to 5.2.1

## [5.2.154]

### Changed

- Update Sentry Java SDK to 5.2.0
- Use Clojure Tools Build for building
- Add in Github Actions for Testing
- If timestamps are provided for a breadcrumb, include them. Thanks @deepxg!

## [5.0.152]

### Changed

- Update Sentry Java SDK to 5.1.2

## [5.0.151]

### Changed

- Update Sentry Java SDK to 5.1.1
- Minor updates

## [5.0.149]

### Changed

- Updated Sentry Java SDK to 5.0.1

## [5.0.147]

### Changed

- Updated Sentry Java SDK to 5.0.0
- No other functional changes

## [4.3.146]

### Changed

- Fix request method as string. Thanks to @danieltdt!

## [4.3.143]

### Fixed

- Allow simple strings (as well as maps) to be used for the message.
  Thanks to @bago2k4 for the contribution! :-) Fixes #23
- Bump Sentry to 4.3.0

## [4.2.139]

### Changed

- Changed CLOJARS_REPO to CLOJARS_URL in the deployment script
- Formatting changes to various documents
- Bump Sentry to 4.2.0

## [3.1.138]

### Added

- Added [Contexts](https://docs.sentry.io/platforms/java/enriching-events/context/)
  to the initialisation of Sentry so that additional information can
  be sent upon each event. Further information contained within the
  README.md and in `sentry_clj/core.clj`. **Note:** `:extras` has been
  deprecated by Sentry - use `:contexts` instead.

## [3.1.137]

### Changed

- Bump Sentry to 3.2.1
- Bump up ring-core to 1.8.2

## [3.1.135]

### Added

- Added in `before-send-fn` and `before-breadcrumb-fn` to the
  initialisation map. This allows for events or breadcrumbs to be
  mutated before sending off to Sentry (or indeed to *prevent* sending
  to Sentry if the respective functions returns nil)

## [3.1.134]

### Changed

- Don't default the environment to "production" if none if found on
  the event, let the Java library do that now.
- Bumped Sentry Java 3.2.0.

## [3.1.130]

### Added

- Added in missing clj-kondo config.

### Changed

- Fix ring handler to work with new Sentry data structure.
- Bumped `deps-deploy`.
- Remove unused 'mocko' library.
- Set warn-on-reflection to be true.

## [3.1.127]

### Added

- Added in a simple deploy script.

### Changed

- Moved to the major.minor.commit model.
- Removed unused library `clojure.java-time`.
- Updated pom.xml with some additional metadata information.
- Bumped Sentry Java to 3.1.1.

## [3.1.0]

### Added

- Added in the ability to define the Environment on configuration of
  Sentry (and not only per-event!)
- Added in the ability to disable the built-in
  UncaughtExceptionHandler in order to allow defining our own.
- Added in a couple of examples:
  - A basic example showing how to register a sentry-logger and how to
    fire an event.
  - An example that registers it's own uncaught exception handler to
    not only log out to sentry, but also to log out to a
    pre-configured logger.

### Changed

- Major release bringing compatibility with version 3.1.0 of the Java
  Sentry library. This is a **BREAKING** change, insofar that this has
  only been tested with the latest Sentry (20.9.0) and is **not**
  compatible with Sentry 10.0.1 and below. If you wish to use those
  versions, please continue to use sentry-clj 1.7.30.

[Unreleased]: https://github.com/getsentry/sentry-clj/compare/6.3.181...HEAD
[6.3.181]: https://github.com/getsentry/sentry-clj/compare/5.7.180...6.3.181
[5.7.180]: https://github.com/getsentry/sentry-clj/compare/5.7.178...5.7.180
[5.7.178]: https://github.com/getsentry/sentry-clj/compare/5.7.177...5.7.178
[5.7.177]: https://github.com/getsentry/sentry-clj/compare/5.7.176...5.7.177
[5.7.176]: https://github.com/getsentry/sentry-clj/compare/5.7.172...5.7.176
[5.7.172]: https://github.com/getsentry/sentry-clj/compare/5.7.171...5.7.172
[5.7.171]: https://github.com/getsentry/sentry-clj/compare/5.6.170...5.7.171
[5.6.170]: https://github.com/getsentry/sentry-clj/compare/5.6.169...5.6.170
[5.6.169]: https://github.com/getsentry/sentry-clj/compare/5.6.166...5.6.169
[5.6.166]: https://github.com/getsentry/sentry-clj/compare/5.5.165...5.6.166
[5.5.165]: https://github.com/getsentry/sentry-clj/compare/5.5.164...5.5.165
[5.5.164]: https://github.com/getsentry/sentry-clj/compare/5.5.163...5.5.164
[5.5.163]: https://github.com/getsentry/sentry-clj/compare/5.5.162...5.5.163
[5.5.162]: https://github.com/getsentry/sentry-clj/compare/5.4.161...5.5.162
[5.4.161]: https://github.com/getsentry/sentry-clj/compare/5.4.160...5.4.161
[5.4.160]: https://github.com/getsentry/sentry-clj/compare/5.3.159...5.4.160
[5.3.159]: https://github.com/getsentry/sentry-clj/compare/5.2.158...5.3.159
[5.2.158]: https://github.com/getsentry/sentry-clj/compare/5.2.157...5.2.158
[5.2.157]: https://github.com/getsentry/sentry-clj/compare/5.2.156...5.2.157
[5.2.156]: https://github.com/getsentry/sentry-clj/compare/5.2.155...5.2.156
[5.2.155]: https://github.com/getsentry/sentry-clj/compare/5.2.154...5.2.155
[5.2.154]: https://github.com/getsentry/sentry-clj/compare/5.0.152...5.2.154
[5.0.152]: https://github.com/getsentry/sentry-clj/compare/5.0.151...5.0.152
[5.0.151]: https://github.com/getsentry/sentry-clj/compare/5.0.149...5.0.151
[5.0.149]: https://github.com/getsentry/sentry-clj/compare/5.0.147...5.0.149
[5.0.147]: https://github.com/getsentry/sentry-clj/compare/4.3.146...5.0.147
[4.3.146]: https://github.com/getsentry/sentry-clj/compare/4.3.143...4.3.146
[4.3.143]: https://github.com/getsentry/sentry-clj/compare/4.2.139...4.3.143
[4.2.139]: https://github.com/getsentry/sentry-clj/compare/3.1.138...4.2.139
[3.1.138]: https://github.com/getsentry/sentry-clj/compare/3.1.137...3.1.138
[3.1.137]: https://github.com/getsentry/sentry-clj/compare/3.1.135...3.1.137
[3.1.135]: https://github.com/getsentry/sentry-clj/compare/3.1.134...3.1.135
[3.1.134]: https://github.com/getsentry/sentry-clj/compare/3.1.127...3.1.134
[3.1.0]: https://github.com/getsentry/sentry-clj/compare/3.1.0...3.1.127
