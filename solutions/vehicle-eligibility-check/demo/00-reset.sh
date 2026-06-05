#!/usr/bin/env bash
# =============================================================================
# 00-reset.sh — Demo state reset
# =============================================================================
# Usage: bash 00-reset.sh [act0|act1|act2]
#
# act0  Before Act 1: web-modeler rewound to pre-BPMN state. Use to restart
#       from the beginning. Presenter must re-run Web Modeler Act 1.
# act1  After Act 1: BPMN present, no worker. Use to skip Act 1 and jump
#       straight to Act 2 (Claude Code worker generation). [DEFAULT]
# act2  After Act 2 local dev: BPMN + buggy worker committed, PR open, CI
#       failing. Use to skip directly to the "CI failure + fix" moment.
#
# References: /gartner setup steps 3 (repo state) and 7 (kill stale workers)
# After reset: run /gartner setup at T-10 min
#
# BRITTLE: Checkpoints are snapshots of a prior run. Web Modeler Copilot
# produces different output each time (variable names, task IDs). After a
# successful full run, update the checkpoints:
#   git checkout web-modeler
#   git tag -f act1-checkpoint <sha-of-end-of-act1-commit>
#   git push --force origin act1-checkpoint
# =============================================================================
set -euo pipefail

TARGET="${1:-act1}"

if [[ "$TARGET" != "act0" && "$TARGET" != "act1" && "$TARGET" != "act2" ]]; then
  echo "Usage: bash $0 [act0|act1|act2]"
  echo "  act0  Before Act 1 — no BPMN synced, Web Modeler manual reset required"
  echo "  act1  After Act 1  — BPMN present, no worker [default]"
  echo "  act2  After Act 2  — BPMN + buggy worker, PR open, CI failing"
  exit 1
fi

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$REPO"
echo "Working in: $REPO"
echo "Target:     $TARGET"
echo ""

# ---------------------------------------------------------------------------
# Step 1: Kill worker (mirrors /gartner setup step 7)
# ---------------------------------------------------------------------------
pkill -f "node.*index.js" 2>/dev/null && echo "KILL: worker stopped" || echo "KILL: no worker running"

# ---------------------------------------------------------------------------
# Step 2: Close any open PR — without deleting the branch.
# gh defaults to upstream repo; --repo override required for fork.
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
# Step 3: Rewind web-modeler to checkpoint.
# act0 → act0-checkpoint (no BPMN synced yet)
# act1 or act2 start → act1-checkpoint (BPMN present, no worker)
# ---------------------------------------------------------------------------
CHECKPOINT_TAG="act1-checkpoint"
[[ "$TARGET" == "act0" ]] && CHECKPOINT_TAG="act0-checkpoint"

if ! git rev-parse "$CHECKPOINT_TAG" >/dev/null 2>&1; then
  echo "ERROR: tag '$CHECKPOINT_TAG' not found locally."
  echo "Fetch with: git fetch origin refs/tags/$CHECKPOINT_TAG:refs/tags/$CHECKPOINT_TAG"
  exit 1
fi

git fetch origin
git push --force origin "refs/tags/$CHECKPOINT_TAG:refs/heads/web-modeler"
echo "BRANCH: web-modeler rewound to $CHECKPOINT_TAG"

# Update local branch to match
git checkout main
git pull origin main
git branch -D web-modeler 2>/dev/null || true
git checkout -b web-modeler origin/web-modeler
git checkout main
echo "LOCAL: web-modeler local branch refreshed"
echo ""

# ---------------------------------------------------------------------------
# act0: manual Web Modeler step required
# ---------------------------------------------------------------------------
if [[ "$TARGET" == "act0" ]]; then
  echo "=== MANUAL STEP REQUIRED ==================================="
  echo "Web Modeler → Maintenance Promo → Vehicle Lookup.bpmn"
  echo "Open History panel → restore the 'scaffold' milestone (v1)"
  echo "This resets the BPMN to the pre-demo state for Copilot Act 1."
  echo "============================================================"
  echo ""
  echo "Reset to act0 complete. Run /gartner setup at T-10 min."
  exit 0
fi

# ---------------------------------------------------------------------------
# act2: commit buggy worker onto web-modeler and open PR
# ---------------------------------------------------------------------------
if [[ "$TARGET" == "act2" ]]; then
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  BUGGY_WORKER="$SCRIPT_DIR/worker-buggy.js"

  if [[ ! -f "$BUGGY_WORKER" ]]; then
    echo "ERROR: worker-buggy.js not found at $BUGGY_WORKER"
    exit 1
  fi

  git checkout web-modeler

  BPMN_FILE=$(ls "solutions/vehicle-eligibility-check/src/main/resources/"*.bpmn 2>/dev/null | head -1)
  if [[ -z "$BPMN_FILE" ]]; then
    echo "ERROR: No BPMN found — act1-checkpoint may be stale"
    exit 1
  fi

  mkdir -p "solutions/vehicle-eligibility-check/worker"
  cp "$BUGGY_WORKER" "solutions/vehicle-eligibility-check/worker/index.js"

  git add "solutions/vehicle-eligibility-check/worker/index.js"
  git commit -m "feat: add vehicle risk assessment worker"
  git push origin web-modeler
  echo "Buggy worker committed and pushed."

  gh pr create \
    --title "Add vehicle-risk-assessment worker" \
    --body "Generated by Claude Code from $(basename "$BPMN_FILE"). Implements io.camunda.demo:vehicle-risk-assessment job type." \
    --base main \
    --head web-modeler \
    --repo HanselIdes/camunda-8-tutorials

  git checkout main
  echo ""
  echo "=== PRESENTER: Switch to GitHub Actions tab. CI is running. ==="
  echo "=== Show CI failure (~20-30s). ==="
  echo ""
  echo "Reset to act2 complete. Run 03-act2-fix.sh when CI failure is visible."
  exit 0
fi

# act1: nothing more to do
echo "Reset to act1 complete. Run /gartner setup at T-10 min."
