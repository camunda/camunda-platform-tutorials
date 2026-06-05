#!/usr/bin/env bash
# =============================================================================
# act2-pr.sh — Act 2 Phase 2a: Commit CPT scenarios, open PR
# =============================================================================
# Run this after: Claude Code has updated the scenarios file (no REPLACE_* remaining)
# Next script:    03-act2-fix.sh  (after CI failure is visible to the audience)
#
# What this does:
#   1. Stages the CPT test scenarios file Claude Code just updated
#   2. Commits and pushes to web-modeler
#   3. Opens a PR against main on the fork
#
# The PR triggers CI which runs CPT tests. Tests will fail because the DMN
# outputs vehicleScore/vehicleEligible but the gateway reads riskScore/eligible.
# The eligible vehicle routes to the ineligible end event — CI catches the drift.
# =============================================================================
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$REPO"

git checkout web-modeler

# Verify no REPLACE_* placeholders remain before committing
SCENARIOS="solutions/vehicle-eligibility-check/test/src/test/resources/scenarios/vehicle-eligibility-check.test.json"
if grep -q "REPLACE_" "$SCENARIOS" 2>/dev/null; then
  echo "ERROR: Scenarios file still contains REPLACE_* placeholders — Claude Code has not finished"
  grep "REPLACE_" "$SCENARIOS"
  exit 1
fi

echo "=== Staging CPT test scenarios ==="
git add solutions/vehicle-eligibility-check/test/
git commit -m "feat: add CPT process tests for vehicle eligibility check"
git push origin web-modeler

echo ""
echo "=== Opening PR ==="
gh pr create \
  --title "Add CPT process tests for vehicle eligibility check" \
  --body "CPT test scenarios verifying eligible and ineligible routing through the Vehicle Eligibility Check process." \
  --base main \
  --head web-modeler \
  --repo HanselIdes/camunda-8-tutorials

echo ""
echo "=== PRESENTER: Switch to GitHub Actions tab. Show CI failure in ~60-90s. ==="
echo "Say: 'CI ran the CPT tests. The eligible vehicle ended at the ineligible end event."
echo "      The DMN outputs vehicleScore and vehicleEligible — the BPMN reads riskScore and eligible."
echo "      The gateway never saw eligible=true, so it took the default path.'"
echo ""
echo "When CI failure is visible, run: bash solutions/vehicle-eligibility-check/demo/03-act2-fix.sh"
