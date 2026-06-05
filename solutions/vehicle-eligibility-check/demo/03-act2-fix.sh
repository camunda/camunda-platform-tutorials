#!/usr/bin/env bash
# =============================================================================
# act2-fix.sh — Act 2 Phase 2b: Fix DMN output column names, push, CI passes
# =============================================================================
# Run this after: CI failure is visible to the audience
# Next script:    04-act3-merge.sh  (after Act 3 Run Play confirmed in Web Modeler)
#
# What this does:
#   1. Renames vehicleScore → riskScore and vehicleEligible → eligible in the DMN
#      output column names. The DMN intentionally uses wrong names to trigger CI
#      failure — this targeted fix is the "pro-code moment" of the demo.
#   2. Commits and pushes. CI re-runs, CPT tests pass, eligible vehicle reaches
#      the correct end event.
# =============================================================================
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$REPO"

git checkout web-modeler

DMN_FILE="solutions/vehicle-eligibility-check/src/main/resources/vehicle-eligibility.dmn"

echo "=== Applying fix: vehicleScore → riskScore, vehicleEligible → eligible ==="
sed -i '' 's/vehicleScore/riskScore/g' "$DMN_FILE"
sed -i '' 's/vehicleEligible/eligible/g' "$DMN_FILE"

echo "Fixed output column names:"
grep -E 'name="(riskScore|eligible|vehicleScore|vehicleEligible)"' "$DMN_FILE"

git add "$DMN_FILE"
git commit -m "fix: correct DMN output column names (riskScore, eligible)"
git push origin web-modeler
echo "Fix pushed. CI re-running."

echo ""
echo "=== ACT 2 COMPLETE ==="
echo "Say: 'Two name changes in the DMN. The gateway now sees eligible=true."
echo "      The eligible vehicle reaches the right end event.'"
echo ""
echo "Ask presenter to confirm before Act 3."
echo "When ready, run: bash solutions/vehicle-eligibility-check/demo/04-act3-merge.sh"
