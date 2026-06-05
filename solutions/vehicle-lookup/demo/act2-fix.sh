#!/usr/bin/env bash
# Trigger: CI failure shown to audience
# Does: find-replace fix, push, start worker, deploy BPMN, start instance
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$REPO"

git checkout web-modeler
BPMN_FILE=$(ls "solutions/vehicle-lookup/"*.bpmn | head -1)

echo "=== Applying find-replace: vehicleScore → riskScore ==="
sed -i '' 's/vehicleScore/riskScore/g' "solutions/vehicle-lookup/worker/index.js"
sed -i '' 's|<zeebe:output source="vehicleScore" target="vehicleScore" />|<zeebe:output source="riskScore" target="riskScore" />|g' "$BPMN_FILE"

git add "solutions/vehicle-lookup/worker/index.js" "$BPMN_FILE"
git commit -m "fix: rename vehicleScore to riskScore — aligns with variable schema"
git push origin web-modeler
echo "Fix pushed. CI re-running."

echo ""
echo "=== Starting worker ==="
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
source ~/.zshrc
c8ctl deploy "$BPMN_FILE"

echo ""
echo "=== Starting process instance ==="
c8ctl create pi --id=vehicle-eligibility-check --variables='{"vin":"2HGFE2F57NH123456"}'
sleep 5
echo ""
echo "Worker result:"
tail -5 /tmp/worker.log

echo ""
echo "=== PRESENTER: Switch to Operate → http://localhost:8080 ==="
echo "=== Show completed instance, tokens at eligible end event. ==="
echo ""
echo "When ready for Act 3, run: bash solutions/vehicle-lookup/demo/act3-merge.sh"
