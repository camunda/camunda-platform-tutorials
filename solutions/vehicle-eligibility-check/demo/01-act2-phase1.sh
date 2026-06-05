#!/usr/bin/env bash
# =============================================================================
# act2-phase1.sh — Act 2 Phase 1: Pull BPMN + print Claude Code prompt
# =============================================================================
# Run this after: Act 1 Phase 1 complete (milestone v1 pushed to web-modeler)
# Next script:    02-act2-pr.sh  (after Claude Code updates scenarios file)
#
# What this does:
#   1. Pulls the web-modeler branch so we have the BPMN Leila just synced
#   2. Copies the CPT scaffold (pom.xml, ProcessTest.java, pre-staged scenarios) into test/
#   3. Prints structural BPMN elements (skips base64 icon data) so presenter can narrate
#   4. Prints the Claude Code prompt for the presenter to paste
#
# Why hard-reset: web-modeler is owned by Web Modeler — we never commit to it
# directly from Claude Code. Reset discards any local stale state.
# =============================================================================
set -euo pipefail

# Resolve repo root from script location (scripts live at solutions/vehicle-eligibility-check/demo/)
REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$REPO"

echo "=== Pulling web-modeler branch ==="
git fetch origin web-modeler
# Create tracking branch on first run; subsequent runs just switch to it
git checkout web-modeler 2>/dev/null || git checkout -b web-modeler origin/web-modeler
git reset --hard origin/web-modeler

# Web Modeler GitHub sync writes BPMN into src/main/resources/ (the Maven layout it picks up).
BPMN_FILE=$(ls "solutions/vehicle-eligibility-check/src/main/resources/"*.bpmn 2>/dev/null | head -1)
if [[ -z "$BPMN_FILE" ]]; then
  echo "ERROR: No BPMN found — Act 1 sync did not complete"
  exit 1
fi

echo "BPMN: $BPMN_FILE"
echo ""
# Show structural BPMN elements — skip base64 icon data embedded by Web Modeler
echo "--- Process structure (from BPMN) ---"
grep -E '(bpmn:process |bpmn:startEvent|bpmn:serviceTask|bpmn:businessRuleTask|bpmn:exclusiveGateway|bpmn:endEvent|bpmn:sequenceFlow)' "$BPMN_FILE" \
  | grep -v 'base64' \
  | sed 's/.*id="\([^"]*\)".*/\1/' \
  | head -20
echo "---"

echo ""
echo "=== Copying CPT scaffold to test/ ==="
SCAFFOLD="solutions/vehicle-eligibility-check/demo/cpt-scaffold"
DEST="solutions/vehicle-eligibility-check/test"
mkdir -p "$DEST"
cp -r "$SCAFFOLD/." "$DEST/"
echo "Scaffold copied to $DEST"
echo ""

echo "=== CLAUDE CODE PROMPT — paste into Claude Code ==="
echo ""
echo "Finish building and testing the process according to the README."
echo ""
echo "=== PRESENTER: Open solutions/vehicle-eligibility-check/README.md in Claude Code, then paste the prompt above. ==="
echo "=== Say: 'Claude reads the README, the BPMN, and the DMN. It knows the routing logic, the element IDs, and what to fill in.' ==="
echo ""
echo "When Claude Code updates the scenarios file, run: bash solutions/vehicle-eligibility-check/demo/02-act2-pr.sh"
