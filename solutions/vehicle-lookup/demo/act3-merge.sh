#!/usr/bin/env bash
# Trigger: Act 3 Run Play confirmed in Web Modeler
# Does: merge PR, print closing line
set -euo pipefail

PR_NUM=$(gh pr list \
  --repo HanselIdes/camunda-8-tutorials \
  --head web-modeler \
  --json number \
  --jq '.[0].number')

if [[ -z "$PR_NUM" ]]; then
  echo "ERROR: No open PR found on web-modeler branch"
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
  cd ~/GitHub/camunda-8-tutorials-fork && bash reset-demo.sh
  Web Modeler: Vehicle Lookup.bpmn → History → restore "scaffold" milestone
CLOSING
