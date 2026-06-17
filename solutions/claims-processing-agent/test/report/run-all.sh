#!/usr/bin/env bash
# Orchestrator for the unified CPT report (Option A).
# Runs the process layer, stashes its artifacts, runs the integration layer (split into two
# separate Maven executions to avoid a CPT 8.10.0-SNAPSHOT context-lifecycle bug that causes
# ClaimsProcessingAgentIT to show tests=0 when it shares a Spring context with ClaimsExternalSystemsIT),
# then generates the requirement-aligned report (which also gates coverage).
set -uo pipefail
cd "$(dirname "$0")/.."          # -> test/
REPORT=report
ART="$REPORT/artifacts"
ENVFILE="../../../.env"

scrub_xml() {                     # $1 = xml file — strip <properties> block that carries secrets
  # Maven Surefire captures all JVM system properties in XML reports, including AWS credentials.
  # Remove the entire <properties>...</properties> block. sed can't match across lines on macOS,
  # so use Python which handles multiline XML correctly.
  python3 - "$1" <<'PYEOF'
import re, sys
path = sys.argv[1]
try:
    text = open(path).read()
    cleaned = re.sub(r'<properties>.*?</properties>', '<properties/>', text, flags=re.DOTALL)
    open(path, 'w').write(cleaned)
except Exception as e:
    print(f"scrub_xml warning: {e}", file=sys.stderr)
PYEOF
}

stash() {                         # $1 = process | cir | pir
  local layer="$1"
  # Map cir/pir to the integration artifact directory.
  local artdir
  if [ "$layer" = "process" ]; then artdir="$ART/process"; else artdir="$ART/integration"; fi
  mkdir -p "$artdir/surefire"

  case "$layer" in
    process)
      rm -f "$artdir/surefire/"*.xml 2>/dev/null || true
      cp -f target/surefire-reports/TEST-io.camunda.tests.ProcessTest.xml "$artdir/surefire/" 2>/dev/null || true
      cp -f target/coverage-report/report.json "$artdir/report.json" 2>/dev/null || true
      ;;
    cir)
      # CIR run: copy only the CIR XML; leave PIR XML untouched.
      cp -f target/surefire-reports/TEST-io.camunda.tests.ClaimsExternalSystemsIT.xml "$artdir/surefire/" 2>/dev/null || true
      # CIR report.json is authoritative for component-layer coverage.
      cp -f target/coverage-report/report.json "$artdir/report.json" 2>/dev/null || true
      ;;
    pir)
      # PIR run: copy only the PIR XML; CIR XML already stashed above.
      cp -f target/surefire-reports/TEST-io.camunda.tests.ClaimsProcessingAgentIT.xml "$artdir/surefire/" 2>/dev/null || true
      # PIR report.json has process-integration coverage — overwrite with this one.
      cp -f target/coverage-report/report.json "$artdir/report.json" 2>/dev/null || true
      ;;
  esac

  for xml in "$artdir/surefire/"*.xml; do [ -f "$xml" ] && scrub_xml "$xml"; done
}

run_mvn_integration() {
  local class="$1"
  if [ -f "$ENVFILE" ]; then
    env $(grep -v '^#' "$ENVFILE" | xargs) mvn -q test -P integration-test "-Dtest=${class}"
  else
    echo "WARN: $ENVFILE not found; running integration without env (will skip credential-gated tests)" >&2
    mvn -q test -P integration-test "-Dtest=${class}"
  fi
}

echo "==> Process tests (mvn test)"
mvn -q test; PROC=$?
stash process

echo "==> Component integration tests — ClaimsExternalSystemsIT"
run_mvn_integration ClaimsExternalSystemsIT; CIR=$?
stash cir

echo "==> Process integration tests — ClaimsProcessingAgentIT"
run_mvn_integration ClaimsProcessingAgentIT; PIR=$?
stash pir

INTEG=$(( CIR | PIR ))

echo "==> Generate unified report (+ coverage gate)"
node "$REPORT/generate-report.mjs"; GATE=$?

echo "process mvn exit=$PROC ; cir mvn exit=$CIR ; pir mvn exit=$PIR ; coverage gate exit=$GATE"
echo "report: $(pwd)/target/unified-report.html"
# Surface a non-zero exit if any layer failed or the gate failed.
[ $PROC -eq 0 ] && [ $GATE -eq 0 ] || exit 1
