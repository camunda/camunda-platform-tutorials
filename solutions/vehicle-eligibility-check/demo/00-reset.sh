#!/usr/bin/env bash
# =============================================================================
# 00-reset.sh — Demo state reset
# =============================================================================
# Usage: bash 00-reset.sh [act0|act1]
#
# act0  Before Act 1: web-modeler rewound to pre-BPMN state. Use to restart
#       from the beginning. Presenter must re-run Web Modeler Act 1.
# act1  After Act 2: BPMN + fixed DMN + CPT tests on web-modeler, ready for
#       Act 3 merge. Also resets main to pre-act2-main (buggy DMN, no tests)
#       so the PR diff shows the column fix red/green. [DEFAULT]
#
# References: /gartner setup steps 3 (repo state) and 7 (kill stale workers)
# After reset: run /gartner setup at T-10 min
#
# BRITTLE: Checkpoints are snapshots of a prior run. Web Modeler Copilot
# produces different output each time (variable names, task IDs). After a
# successful full run, update both tags:
#   git tag -f pre-act2-main <sha-of-web-modeler-sync-commit>   # BPMN + buggy DMN
#   git tag -f act1-checkpoint <sha-after-cpt-and-dmn-fix>      # BPMN + fixed DMN + tests
#   git push --force origin pre-act2-main act1-checkpoint
# =============================================================================
set -euo pipefail

TARGET="${1:-act1}"

if [[ "$TARGET" != "act0" && "$TARGET" != "act1" ]]; then
  echo "Usage: bash $0 [act0|act1]"
  echo "  act0  Before Act 1 — no BPMN synced, Web Modeler manual reset required"
  echo "  act1  After Act 1  — BPMN + DMN present, no CPT tests [default]"
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
# Step 3: Rewind web-modeler to checkpoint + reset main to pre-act2 baseline.
# act0 → act0-checkpoint (no BPMN synced yet)
# act1 → act1-checkpoint (BPMN + fixed DMN + CPT tests — post-Act-2 state)
#         main → pre-act2-main (BPMN + buggy DMN, no CPT tests)
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

# For act1, also reset main to the pre-Act-2 state so Act 3 PR is always clean.
# pre-act2-main = main + buggy DMN, no CPT tests; act1-checkpoint parent.
if [[ "$TARGET" == "act1" ]]; then
  if git rev-parse "pre-act2-main" >/dev/null 2>&1; then
    git push --force origin "refs/tags/pre-act2-main:refs/heads/main"
    echo "BRANCH: main rewound to pre-act2-main"

    # CRITICAL: verify act1-checkpoint is a linear descendant of pre-act2-main.
    # If not, the PR will have mergeable_state=dirty and GitHub will silently suppress
    # all pull_request CI — no checks fire, no errors, just silence.
    PRE=$(git rev-parse pre-act2-main)
    ACT1=$(git rev-parse act1-checkpoint)
    if git merge-base --is-ancestor "$PRE" "$ACT1" 2>/dev/null; then
      echo "ANCESTRY: act1-checkpoint descends from pre-act2-main ✓"
    else
      echo ""
      echo "ERROR: act1-checkpoint does NOT descend from pre-act2-main."
      echo "       The PR will be dirty and CI will not fire."
      echo "       Rebuild checkpoints using the hygiene steps in SKILL.md before the demo."
      echo "       (act1-checkpoint must be rebased onto pre-act2-main)"
      exit 1
    fi
  else
    echo "WARN: tag 'pre-act2-main' not found; main not reset. PR may conflict."
    echo "      Fetch with: git fetch origin refs/tags/pre-act2-main:refs/tags/pre-act2-main"
  fi
fi

# Update local branch to match
git checkout main
git pull origin main
git branch -D web-modeler 2>/dev/null || true
git checkout -b web-modeler origin/web-modeler
git checkout main
echo "LOCAL: web-modeler + main local branches refreshed"
echo ""

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

# act1: nothing more to do
echo "Reset to act1 complete. Run /gartner setup at T-10 min."
