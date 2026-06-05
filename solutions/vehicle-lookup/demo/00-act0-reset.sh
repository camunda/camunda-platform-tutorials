#!/usr/bin/env bash
# =============================================================================
# 00-act0-reset.sh — Pre-demo reset
# =============================================================================
# Trigger: after any demo run, before starting the next one
# Does:    kill worker → close PR → rewind web-modeler to act1-checkpoint
# References: /gartner setup steps 3 (repo state) and 7 (kill stale workers)
# After this script: run /gartner setup at T-10 min
#
# BRITTLE: The act1-checkpoint tag captures the BPMN as it was at the end of
# Act 1 in a prior demo run. Web Modeler Copilot produces different output each
# time (variable names, task IDs, structure). The checkpoint BPMN may not match
# what Copilot generates in the next run, causing 03-act2-fix.sh to fail.
# After each successful full-run demo, update the checkpoint:
#   git checkout web-modeler
#   git tag -f act1-checkpoint <sha-of-end-of-act1-commit>
#   git push --force origin act1-checkpoint
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
# Step 2: Close any open PR on web-modeler — without deleting the branch.
# gh defaults to upstream repo; --repo override required for fork.
# The branch stays alive; we rewind it to the checkpoint in step 3.
# ---------------------------------------------------------------------------
PR_NUM=$(gh pr list \
  --repo HanselIdes/camunda-8-tutorials \
  --head web-modeler \
  --state open \
  --json number \
  --jq '.[0].number' 2>/dev/null || true)

if [[ -n "$PR_NUM" ]]; then
  gh pr close "$PR_NUM" --repo HanselIdes/camunda-8-tutorials
  echo "PR:   closed #$PR_NUM (branch kept)"
else
  echo "PR:   none open"
fi

# ---------------------------------------------------------------------------
# Step 3: Rewind web-modeler to act1-checkpoint
# Force-push the checkpoint tag to the remote branch — no deletion.
# act1-checkpoint must exist as a tag pointing to the end-of-Act-1 commit.
# ---------------------------------------------------------------------------
if ! git rev-parse act1-checkpoint >/dev/null 2>&1; then
  echo "ERROR: tag 'act1-checkpoint' not found locally."
  echo "Fetch it with: git fetch origin refs/tags/act1-checkpoint:refs/tags/act1-checkpoint"
  echo "Or create it: git tag act1-checkpoint <end-of-act1-sha> && git push origin act1-checkpoint"
  exit 1
fi

git fetch origin
git push --force origin refs/tags/act1-checkpoint:refs/heads/web-modeler
echo "BRANCH: web-modeler rewound to act1-checkpoint"

# Update local branch to match
git checkout main
git pull origin main
git branch -D web-modeler 2>/dev/null || true
git checkout -b web-modeler origin/web-modeler
git checkout main
echo "LOCAL: web-modeler local branch refreshed"

echo ""
echo "=== MANUAL STEP REQUIRED ==================================="
echo "Web Modeler → Maintenance Promo → Vehicle Lookup.bpmn"
echo "Open History panel → restore the 'scaffold' milestone (v1)"
echo "This resets the BPMN to the pre-demo state for Copilot Act 1."
echo "============================================================"
echo ""
echo "Reset complete. Run /gartner setup at T-10 min before next demo."
