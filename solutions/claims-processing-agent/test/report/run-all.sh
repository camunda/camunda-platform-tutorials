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

stash() {                         # $1 = process | integration
  mkdir -p "$ART/$1/surefire"
  rm -f "$ART/$1/surefire/"*.xml 2>/dev/null || true
  cp -f target/surefire-reports/TEST-*.xml "$ART/$1/surefire/" 2>/dev/null || true
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
