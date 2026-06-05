#!/usr/bin/env bash
# =============================================================================
# act2-phase1.sh — Act 2 Phase 1: Pull BPMN + print Claude Code prompt
# =============================================================================
# Run this after: Act 1 Phase 1 complete (milestone v1 pushed to web-modeler)
# Next script:    act2-pr.sh  (after Claude Code generates worker AND commit 2 pushed)
#
# What this does:
#   1. Pulls the web-modeler branch so we have the BPMN Leila just synced
#   2. Prints the first 30 lines so the presenter can show Claude reading raw XML
#   3. Prints the exact Claude Code prompt for the presenter to paste
#
# Why hard-reset: web-modeler is owned by Web Modeler — we never commit to it
# directly from Claude Code. Reset discards any local stale state.
# BRITTLE: The Claude Code prompt below references 'vehicle-lookup.bpmn' by a
# fixed path. Web Modeler may have renamed the file from the diagram title
# (e.g., 'Vehicle Eligibility Check.bpmn'). The BPMN_FILE var below discovers
# the actual filename — update the prompt text if it differs significantly.
# Also: the prompt assumes a specific scoring algorithm and variable contract.
# If Copilot generated different output variable names in Act 1, update the
# prompt to match before pasting into Claude Code.
# =============================================================================
set -euo pipefail

# Resolve repo root from script location (scripts live at solutions/vehicle-lookup/demo/)
REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$REPO"

echo "=== Pulling web-modeler branch ==="
git fetch origin web-modeler
# Create tracking branch on first run; subsequent runs just switch to it
git checkout web-modeler 2>/dev/null || git checkout -b web-modeler origin/web-modeler
git reset --hard origin/web-modeler

# Web Modeler names the file and folder from the diagram title, which may change each run.
# Check vehicle-eligibility-check/ first (current Web Modeler sync target), fall back to vehicle-lookup/.
BPMN_FILE=$(ls "solutions/vehicle-eligibility-check/"*.bpmn 2>/dev/null | head -1)
if [[ -z "$BPMN_FILE" ]]; then
  BPMN_FILE=$(ls "solutions/vehicle-lookup/"*.bpmn 2>/dev/null | head -1)
fi
if [[ -z "$BPMN_FILE" ]]; then
  echo "ERROR: No BPMN found — Act 1 sync did not complete"
  exit 1
fi

echo "BPMN: $BPMN_FILE"
echo ""
# Show raw XML so the presenter can narrate "Claude reads standard BPMN — no proprietary format"
head -30 "$BPMN_FILE"

echo ""
echo "=== CLAUDE CODE PROMPT — paste into Claude Code ==="
echo ""
cat <<PROMPT
  "Read $BPMN_FILE and find the service task that handles vehicle risk assessment.

   Implement its Zeebe Node.js worker. The worker needs to integrate with
   the process data coming from the NHTSA vehicle lookup — pull out the
   vehicle details and run a simple scoring algorithm: start at 50, give
   newer vehicles a break (2018+), and favor common passenger vehicles. If
   the final score is 40 or under, the vehicle is eligible.

   Use the Zeebe REST API directly with Node.js built-in http — no external
   packages. The local Camunda instance runs at http://localhost:8080 with
   basic auth demo:demo. Poll for jobs and complete them via the REST API.
   Write to solutions/vehicle-lookup/worker/index.js."
PROMPT
echo ""
echo "=== PRESENTER: Switch to Claude Code. Paste prompt above. ==="
echo "=== While Claude generates, narrate: \"Claude read the BPMN and knew exactly what to implement.\" ==="
