#!/usr/bin/env bash
# Run CPT tests in isolated env so SaaS CAMUNDA_*/ZEEBE_* shell vars
# don't override application.yml runtime-mode: shared.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

JAVA_HOME="${JAVA_HOME:-$HOME/.asdf/installs/java/temurin-21.0.9+10.0.LTS}"
MAVEN_HOME="${MAVEN_HOME:-$HOME/.asdf/installs/maven/3.9.11}"
DOCKER_HOST="${DOCKER_HOST:-unix://$HOME/.docker/run/docker.sock}"

env -i \
  HOME="$HOME" \
  PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin" \
  JAVA_HOME="$JAVA_HOME" \
  DOCKER_HOST="$DOCKER_HOST" \
  mvn -f "$SCRIPT_DIR/pom.xml" test "$@"
