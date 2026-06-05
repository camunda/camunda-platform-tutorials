#!/usr/bin/env bash
# Trigger: Act 1 Phase 1 done (milestone v1 pushed to web-modeler)
# Does: pull BPMN from web-modeler, print Claude Code prompt
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$REPO"

echo "=== Pulling web-modeler branch ==="
git fetch origin web-modeler
git checkout web-modeler 2>/dev/null || git checkout -b web-modeler origin/web-modeler
git reset --hard origin/web-modeler

BPMN_FILE=$(ls "solutions/vehicle-lookup/"*.bpmn 2>/dev/null | head -1)
if [[ -z "$BPMN_FILE" ]]; then
  echo "ERROR: No BPMN found — Act 1 sync did not complete"
  exit 1
fi

echo "BPMN: $BPMN_FILE"
echo ""
head -30 "$BPMN_FILE"

cat <<'PROMPT'

=== CLAUDE CODE PROMPT — paste into Claude Code ===

  "Read solutions/vehicle-lookup/vehicle-lookup.bpmn and find the service
   task that handles vehicle risk assessment.

   Implement its Zeebe Node.js worker. The worker needs to integrate with
   the process data coming from the NHTSA vehicle lookup — pull out the
   vehicle details and run a simple scoring algorithm: start at 50, give
   newer vehicles a break (2018+), and favor common passenger vehicles. If
   the final score is 40 or under, the vehicle is eligible.

   Use the Zeebe REST API directly with Node.js built-in http — no external
   packages. The local Camunda instance runs at http://localhost:8080 with
   basic auth demo:demo. Poll for jobs and complete them via the REST API.
   Write to solutions/vehicle-lookup/worker/index.js."

=== PRESENTER: Switch to Claude Code. Paste prompt above. ===
=== While Claude generates, narrate: "Claude read the BPMN and knew exactly what to implement." ===
PROMPT
