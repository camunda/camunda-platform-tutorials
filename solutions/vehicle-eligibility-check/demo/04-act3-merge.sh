#!/usr/bin/env bash
# =============================================================================
# act3-merge.sh — Act 3: Merge PR and print closing
# =============================================================================
# Run this after: Act 3 Run Play confirmed in Web Modeler
#
# What this does:
#   1. Discovers the open PR number on the web-modeler branch dynamically
#      (avoids hardcoding a number that changes between demo runs)
#   2. Squash-merges and deletes the branch — clean history, single commit
#   3. Prints the closing line and post-demo reset instructions
#
# Why --repo HanselIdes/camunda-8-tutorials: gh defaults to the upstream
# camunda/camunda-8-tutorials. Always specify the fork explicitly.
#
# Why squash: collapses the skeleton → FEEL → worker → fix commits into one
# clean merge commit, which reads well in the upstream repo history.
# =============================================================================
set -euo pipefail

# Discover PR number dynamically — changes between demo runs
PR_NUM=$(gh pr list \
  --repo HanselIdes/camunda-8-tutorials \
  --head web-modeler \
  --json number \
  --jq '.[0].number')

if [[ -z "$PR_NUM" ]]; then
  echo "ERROR: No open PR found on web-modeler branch"
  echo "Check: gh pr list --repo HanselIdes/camunda-8-tutorials"
  exit 1
fi

echo "Merging PR #$PR_NUM..."
gh pr merge "$PR_NUM" \
  --repo HanselIdes/camunda-8-tutorials \
  --squash \
  --delete-branch

cat <<'CLOSING'

=== CLOSING LINE ===
"Open standards. Any AI tool. Guardrails that travel with the process."

TOTAL TARGET: 5:30 | HARD CAP: 7:00

=== POST-DEMO RESET ===
  cd ~/GitHub/camunda-8-tutorials-fork && bash solutions/vehicle-lookup/demo/00-reset.sh act1
  Web Modeler: Vehicle Lookup.bpmn → History → restore "scaffold" milestone
CLOSING
