#
# And awayyyy we go!
#

set dotenv-load
set quiet

# List all recipes (_ == hidden recipe)
_default:
    just --list

# Cat the Justfile
cat:
    just --dump

# Upgrade dependencies
deps:
    clojure -X:antq

# Checks (or formats) the source code
format action="check" files="":
    clojure -M:format-{{ action }} {{ files }}

# Run tests
test:
    bin/test

# Build the JAR
build:
    bin/build

# Install the JAR to your local .m2 repository
install: build
	bin/install

# Publish the JAR to Clojars
publish: build
	bin/publish

# Build and publish the JAR to Clojars
all: build publish

# Install pre-commit (https://pre-commit.com/)
pre-commit-install:
    pre-commit install

# Run pre-commit hooks (to verify at any point, not just on commit)
pre-commit-run hook-id="":
    pre-commit run --all-files {{ hook-id }}

# vim: expandtab:ts=4:sw=4:ft=just
