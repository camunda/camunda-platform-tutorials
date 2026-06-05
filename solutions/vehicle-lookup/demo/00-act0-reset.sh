#!/usr/bin/env bash
# =============================================================================
# 00-act0-reset.sh — Pre-demo reset
# =============================================================================
# Trigger: after any demo run, before starting the next one
# Does:    kill worker → close PR → delete web-modeler branch → return to main
# References: /gartner setup steps 3 (repo state) and 7 (kill stale workers)
# After this script: run /gartner setup at T-10 min
# =============================================================================
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$REPO"
echo "Working in: $REPO"
echo ""

# ---------------------------------------------------------------------------
# Step 1: Kill worker (mirrors /gartner setup step 7)
# ---------------------------------------------------------------------------
pkill -f "node.*index.js" 2>/dev/null && echo "KILL: worker stopped" || echo "KILL: no worker running"

# ---------------------------------------------------------------------------
# Step 2: Close open PR on web-modeler branch, then delete remote branch
# Must close PR before branch delete — otherwise gh leaves a dangling PR.
# gh defaults to upstream repo; --repo override required for fork.
# ---------------------------------------------------------------------------
PR_NUM=$(gh pr list \
  --repo HanselIdes/camunda-8-tutorials \
  --head web-modeler \
  --state open \
  --json number \
  --jq '.[0].number' 2>/dev/null || true)

if [[ -n "$PR_NUM" ]]; then
  gh pr close "$PR_NUM" --repo HanselIdes/camunda-8-tutorials --delete-branch
  echo "PR:   closed #$PR_NUM and deleted remote web-modeler branch"
else
  # No open PR — delete remote branch directly (mirrors /gartner setup step 3)
  git push origin --delete web-modeler 2>/dev/null \
    && echo "BRANCH: deleted remote web-modeler" \
    || echo "BRANCH: remote web-modeler not found (already clean)"
fi

# ---------------------------------------------------------------------------
# Step 3: Return to main and delete local branch (mirrors /gartner setup step 3)
# ---------------------------------------------------------------------------
git checkout main
git pull origin main
git branch -D web-modeler 2>/dev/null \
  && echo "BRANCH: deleted local web-modeler" \
  || echo "BRANCH: local web-modeler not found (already clean)"

echo ""
echo "=== MANUAL STEP REQUIRED ==================================="
echo "Web Modeler → Maintenance Promo → Vehicle Lookup.bpmn"
echo "Open History panel → restore the 'scaffold' milestone (v1)"
echo "This resets the BPMN to the pre-demo state for Copilot Act 1."
echo "============================================================"
echo ""
echo "Reset complete. Run /gartner setup at T-10 min before next demo."
