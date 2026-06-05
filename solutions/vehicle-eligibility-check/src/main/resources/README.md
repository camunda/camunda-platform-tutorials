# Vehicle Eligibility Check

Gartner BOAT MQ 2026 demo solution. Demonstrates AI-assisted process development:

1. **Web Modeler Copilot** generates the process (VIN lookup → eligibility DMN → XOR → eligible / ineligible path)
2. **FEEL Copilot** writes the result expression on the VIN lookup task
3. **Claude Code** reads this README and the BPMN, fills in the CPT test scenarios, and the PR runs CI
4. **CI (CPT)** runs process tests and reveals variable drift between the DMN output and the gateway condition
5. A targeted fix to the DMN output column names → CI passes → merge to production

## Process variables

| Variable          | Type    | Source                        |
|-------------------|---------|-------------------------------|
| `vin`             | string  | Process start                 |
| `year`            | number  | VIN lookup output mapping     |
| `make`            | string  | VIN lookup output mapping     |
| `model`           | string  | VIN lookup output mapping     |
| `vehicle type`    | string  | VIN lookup output mapping     |
| `riskScore`       | number  | Eligibility DMN               |
| `eligible`        | boolean | Eligibility DMN               |

## DMN decision

`vehicle-eligibility.dmn` — decision ID `vehicle-eligibility`. Input columns: `year`, `vehicle type`. Output columns: `riskScore` (number), `eligible` (boolean).

The gateway condition is `=eligible`. If the DMN output column names drift from `riskScore` / `eligible`, the gateway never sees `eligible = true` and routes every instance to the ineligible end event.

## CPT test scenarios

**3. CPT test scenarios** (`test/src/test/resources/scenarios/vehicle-eligibility-check.test.json`)

The test harness (`pom.xml`, `ProcessTest.java`) and a partially-complete scenarios file are already in the `test/` folder. Update the scenarios file: replace every `REPLACE_*` placeholder with the actual value from the BPMN.

- `REPLACE_PROCESS_DEFINITION_ID` → the `id` attribute of the `<bpmn:process>` element
- `REPLACE_VIN_LOOKUP_TASK_ID` → element ID of the NHTSA VIN lookup service task
- `REPLACE_ELIGIBILITY_TASK_ID` → element ID of the business rule task (Calculate Eligibility)
- `REPLACE_ELIGIBLE_DOWNSTREAM_TASK_ID` → element ID of the task on the eligible path (after the gateway, before the eligible end event)
- `REPLACE_INELIGIBLE_DOWNSTREAM_TASK_ID` → element ID of the task on the ineligible path (after the gateway, before the ineligible end event)
- `REPLACE_ELIGIBLE_END_EVENT_ID` → element ID of the end event on the eligible path
- `REPLACE_INELIGIBLE_END_EVENT_ID` → element ID of the end event on the ineligible path

If a path has no downstream task between the gateway and the end event, remove that `COMPLETE_JOB` instruction for that path. Use element IDs from the BPMN.
