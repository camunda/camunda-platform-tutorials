#!/usr/bin/env bash
# =============================================================================
# act2-fix.sh — Act 2 Phase 2b: Fix variable drift, deploy, start instance
# =============================================================================
# Run this after: CI failure is visible to the audience
# Next script:    04-act3-merge.sh  (after Act 3 Run Play confirmed in Web Modeler)
#
# What this does:
#   1. Renames vehicleScore → riskScore in both the worker and the BPMN output
#      mapping. The worker intentionally uses the wrong variable name to trigger
#      a CI failure — this find-replace is the "pro-code moment" of the demo.
#   2. Commits and pushes the fix so CI re-runs and passes
#   3. Kills any stale worker process, starts a fresh one against local Camunda
#   4. Deploys the BPMN to the local c8run instance
#   5. Starts a process instance with the test VIN and shows the worker result
#
# Why source ~/.zshrc: c8ctl reads CAMUNDA_CLIENT_ID / CAMUNDA_CLIENT_SECRET
# from the shell profile. Without it, deploy and instance creation fail silently.
#
# Why /tmp/worker.log: keeps worker output accessible for tail without cluttering
# the repo directory. Log is overwritten on each demo run.
# =============================================================================
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$REPO"

git checkout web-modeler
# Discover BPMN filename dynamically — Web Modeler names it from the diagram title
BPMN_FILE=$(ls "solutions/vehicle-lookup/"*.bpmn | head -1)

echo "=== Applying find-replace: vehicleScore → riskScore ==="
# Fix all occurrences in the worker (variable name used in scoring and job completion)
sed -i '' 's/vehicleScore/riskScore/g' "solutions/vehicle-lookup/worker/index.js"
# Fix the BPMN output mapping (zeebe:output source and target attributes)
sed -i '' 's|<zeebe:output source="vehicleScore" target="vehicleScore" />|<zeebe:output source="riskScore" target="riskScore" />|g' "$BPMN_FILE"

git add "solutions/vehicle-lookup/worker/index.js" "$BPMN_FILE"
git commit -m "fix: rename vehicleScore to riskScore — aligns with variable schema"
git push origin web-modeler
echo "Fix pushed. CI re-running."

echo ""
echo "=== Starting worker ==="
# Kill any prior worker from a previous demo run to avoid duplicate job completion
pkill -f "node.*index.js" 2>/dev/null && echo "Killed stale worker" || true
sleep 1
cd "$REPO/solutions/vehicle-lookup/worker"
nohup node index.js > /tmp/worker.log 2>&1 &
echo "Worker PID: $!"
sleep 3
tail -3 /tmp/worker.log

echo ""
echo "=== Deploying BPMN ==="
cd "$REPO"
# c8ctl credentials come from env vars set in .zshrc
source ~/.zshrc
c8ctl deploy "$BPMN_FILE"

echo ""
echo "=== Starting process instance ==="
# Test VIN: 2023 Honda Civic — scores eligible (riskScore ≤ 40)
c8ctl create pi --id=vehicle-eligibility-check --variables='{"vin":"2HGFE2F57NH123456"}'
sleep 5
echo ""
echo "Worker result:"
tail -5 /tmp/worker.log

echo ""
echo "=== PRESENTER: Switch to Operate → http://localhost:8080 ==="
echo "=== Show completed instance, tokens at eligible end event. ==="
echo ""
echo "When ready for Act 3, run: bash solutions/vehicle-lookup/demo/04-act3-merge.sh"
