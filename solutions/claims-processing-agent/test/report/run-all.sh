#!/usr/bin/env bash
# Orchestrator for the unified CPT report (Option A).
# Runs the process layer, stashes its artifacts, runs the integration layer, stashes its
# artifacts, then generates the requirement-aligned report (which also gates coverage).
# Two Maven invocations overwrite target/coverage-report/report.json, so each run's
# artifacts are copied aside immediately after it finishes.
set -uo pipefail
cd "$(dirname "$0")/.."          # -> test/
REPORT=report
ART="$REPORT/artifacts"
ENVFILE="../../../.env"

scrub_xml() {                     # $1 = xml file — strip <property> elements that carry secrets
  # Maven Surefire captures all JVM system properties in XML reports, including AWS credentials
  # passed as -D flags. Remove the <properties> block entirely; it adds no value to the report.
  sed -i "" 's/<properties>.*<\/properties>//g' "$1" 2>/dev/null || true
  # Fallback: redact any remaining value attributes that look like AWS key patterns.
  sed -i "" 's/value="AKIA[A-Z0-9]*"/value="***REDACTED***"/g' "$1" 2>/dev/null || true
}

stash() {                         # $1 = process | integration
  mkdir -p "$ART/$1/surefire"
  rm -f "$ART/$1/surefire/"*.xml 2>/dev/null || true
  # Copy only the test-class XMLs from this layer; exclude XMLs from the other layer
  # (target/surefire-reports/ is shared across both mvn runs).
  if [ "$1" = "process" ]; then
    cp -f target/surefire-reports/TEST-io.camunda.tests.ProcessTest.xml "$ART/$1/surefire/" 2>/dev/null || true
  else
    cp -f target/surefire-reports/TEST-io.camunda.tests.ClaimsExternalSystemsIT.xml "$ART/$1/surefire/" 2>/dev/null || true
    cp -f target/surefire-reports/TEST-io.camunda.tests.ClaimsProcessingAgentIT.xml "$ART/$1/surefire/" 2>/dev/null || true
  fi
  for xml in "$ART/$1/surefire/"*.xml; do [ -f "$xml" ] && scrub_xml "$xml"; done
  cp -f target/coverage-report/report.json "$ART/$1/report.json" 2>/dev/null || true
}

echo "==> Process tests (mvn test)"
mvn -q test; PROC=$?
stash process

echo "==> Integration tests (mvn test -P integration-test)"
if [ -f "$ENVFILE" ]; then
  env $(grep -v '^#' "$ENVFILE" | xargs) mvn -q test -P integration-test; INTEG=$?
else
  echo "WARN: $ENVFILE not found; running integration without env (will skip credential-gated tests)"
  mvn -q test -P integration-test; INTEG=$?
fi
stash integration

echo "==> Generate unified report (+ coverage gate)"
node "$REPORT/generate-report.mjs"; GATE=$?

echo "process mvn exit=$PROC ; integration mvn exit=$INTEG ; coverage gate exit=$GATE"
echo "report: $(pwd)/target/unified-report.html"
# Surface a non-zero exit if any layer failed or the gate failed.
[ $PROC -eq 0 ] && [ $GATE -eq 0 ] || exit 1
