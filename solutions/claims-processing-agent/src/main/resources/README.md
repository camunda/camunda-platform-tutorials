# Demo Runbook - Claims Processing Agent (8.10)

## What this demo shows

A fraud-escalation walk-through for Camunda's agentic orchestration capabilities on **8.10 (snapshot)**.
The headline is **agent visibility** - the in-development 8.10 Operate agent view.

The fraud scenario drives a rich multi-tool-call conversation so the agent view has substance to display:
- Full conversation history (system, user, assistant messages)
- Three sequential tool calls with inputs and results
- `<thinking>` reasoning traces before each tool call
- Token usage
- Then: the judge reviews the report, decides ESCALATE, and the adjuster task surfaces the full report + verdict

---

## Pre-flight checklist

### Cluster prerequisites

Your SaaS cluster needs these secrets configured before deploy:
- `AWS_BEDROCK_REGION`
- `AWS_BEDROCK_ACCESS_KEY`
- `AWS_BEDROCK_SECRET_KEY`
- `AWS_BEDROCK_MODEL`

---

## Deploy

### Configure c8ctl profile (one-time)

Get your Zeebe REST address and OAuth client credentials from the Camunda SaaS console, then:

```bash
c8ctl add profile <your-profile-name> \
  --baseUrl '<your-zeebe-rest-address>' \
  --clientId '<your-client-id>' \
  --clientSecret '<your-client-secret>' \
  --audience 'zeebe.camunda.io' \
  --oAuthUrl 'https://login.cloud.camunda.io/oauth/token'
```

### Deploy all resources

```bash
cd claims-processing-agent
c8ctl deploy . --profile=<your-profile-name>
```

Expected output: 6 resources deployed (1 BPMN + 5 forms), process ID `CamundaInsurance_ClaimsProcessing`.

---

## Run the demo

### Step 1 - Submit the claim

Open Tasklist and start the `CamundaInsurance_ClaimsProcessing` process.
The form is pre-filled with Maria Kovacs / CLM-2025-0042. Submit as-is.

Alternatively via CLI:
```bash
c8ctl create pi --profile=<your-profile-name> \
  --bpmnProcessId CamundaInsurance_ClaimsProcessing \
  --variables='{"customerName":"Maria Kovacs","customerId":"CUST-4521","customerEmail":"maria.kovacs@email.com","claimId":"CLM-2025-0042","claimType":"collision","incidentDate":"2026-06-01","damageDescription":"Rear-end collision at traffic light. Other driver confirmed at fault. Police report filed on-site (report #WR-2026-44821). Damage to rear bumper and trunk. Repair estimate from certified body shop attached."}'
```

### Step 2 - Wait (~60-120 seconds)

The assessment agent makes three sequential Bedrock calls. Each tool call has a 15-second simulated
delay on the mock API to make the external system interaction visible - this is intentional, not a bug.
Total wait is ~60-120 seconds depending on Bedrock latency.

### Step 3 - Open Operate and show the agent view

This is the headline. Open the process instance in Operate.

**What to point at:**

1. **Process flow** - the assessment agent (ad-hoc sub-process) ran and the judge already evaluated.
   Click through the execution tree.

2. **Agent context variable** - select the `Agent_ClaimsAssessment` element, then inspect the
   `agentContext` variable. In the 8.10 in-development agent view, this renders as:
   - Conversation history (system prompt, user prompt, assistant replies)
   - Three tool calls: `PolicyLookup`, `GetCustomerProfile`, `CalculateDamageEstimate` - each with
     inputs and results
   - `<thinking>` blocks before each tool call showing the agent's reasoning
   - Token usage summary

   If the in-dev agent view UI is not yet wired in the build, open the raw JSON of `agentContext`
   directly - all the data is there.

3. **Tool call inputs** - show `PolicyLookup` was called with `claimId: CLM-2025-0042` and returned
   `fraudRiskScore: high` with 3 fraud flag reasons. Show `GetCustomerProfile` with
   `customerId: CUST-4521` returning `fraudHistory: true`, 2 prior investigations. Show
   `CalculateDamageEstimate` returning claimed amount 5.2x vehicle market value.

4. **The judge** - show the `Agent_Judge` element. Its input was `agent.responseText` (the full
   assessment report from the assessor). It returned `judgeDecision: ESCALATE` and a
   `judgeReasoning` explaining the fraud indicators.

5. **Gateway routing** - show the gateway routed to escalation (judge-driven, not hardcoded).

6. **Escalated claim user task** - the instance parked at "Handle escalated claim". Open the task in
   Tasklist. The adjuster sees:
   - Full AI assessment report
   - Judge verdict (ESCALATE), reasoning, confidence
   - Suggested settlement amount
   - All claim details

### Step 4 - Complete the adjuster task (optional)

Pick up the task in Tasklist, select "Deny - Fraud Suspected", add adjuster notes, submit. The
process ends.

---

## Narrative arc for the demo

> "Maria Kovacs submitted a collision claim. Our assessment agent ran through the full workflow -
> validated the policy, pulled the customer risk profile, and calculated the estimate. But look at
> what it found: collision coverage added 12 days before the incident, 5 prior claims, open fraud
> investigation, estimated amount 5x the vehicle value.
>
> The agent documented all of that in its reasoning traces - you can see exactly what it was
> thinking before each tool call. It completed the full report and let the judge decide.
>
> The judge - an independent LLM reviewer - read the report and escalated to a human adjuster
> with clear reasoning. The adjuster's task surfaces everything: the AI assessment, the judge's
> verdict, and all the fraud signals. One click to deny for fraud.
>
> This is what agent visibility looks like in 8.10: you don't just see the outcome - you see
> every reasoning step."

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Mock API returns error on estimate call | Beeceptor free plan limit hit or rule missing | Contact Aleksander |
| Agent calls `/customers/CLM-2025-0042` instead of `/customers/CUST-4521` | Wrong BPMN version deployed | Redeploy from this zip |
| MaxModelCalls incident on assessor | `maxModelCalls` too low | Confirm BPMN has `maxModelCalls=20` on the assessor |
| MaxModelCalls incident on judge | `maxModelCalls` too low | Confirm BPMN has `maxModelCalls=5` on the judge |
| Instance always escalates regardless of judge decision | Gateway conditions wrong | Redeploy from this zip |
| Bedrock incident | Secrets missing or wrong model ID | Check `AWS_BEDROCK_*` secrets on cluster |

---

---

# Section 4: Testing Demo

**Presenter**: Eric  
**Time**: ~30 min  
**Confidence**: 5/5 — committed

## What this section shows

The first question any regulated customer asks is: how do you test something non-deterministic?

This section answers it. Using the same claims-processing-agent BPMN, it demonstrates:
- AI-assisted test generation from a process definition (pro-code, Claude Code)
- Deterministic assertions on agentic behavior (low-code, Web Modeler Test tab)
- Regression detection before production (model swap: Opus to Haiku)
- CI/CD gate on the same test artifacts (pipeline portability)

Two surfaces: Claude Code for generation and pro-code execution; Web Modeler Test tab for low-code
runs and manual segment tests.

---

## Pre-flight checklist (testing section)

In addition to the cluster prerequisites above:

- [ ] Claude Code installed with `/camunda-process-test` skill available
- [ ] Java 21 + Maven installed locally
- [ ] `solutions/claims-processing-agent/` opened in Claude Code terminal
- [ ] Camunda Modeler open with `claims-processing-agent.bpmn` loaded
- [ ] Test tab visible in Web Modeler (8.10 snapshot build)

---

## Click path

### Surface 1: Claude Code — generate and validate

**Step 1: Generate test cases from the BPMN**

Open a terminal in `solutions/claims-processing-agent/`. Run:

```
/camunda-process-test
```

Point the skill at `src/main/resources/claims-processing-agent.bpmn`.

Expected output: CPT test JSON files written to `src/main/resources/`. Show the generated files
in the file tree.

*Narration*: "You have an agentic process. Zero test coverage. One skill invocation — and now
you have a starting suite generated from your process definition. You did not write these."

---

**Step 2: Run agentic test methods locally**

Run the test suite with the CPT statistical runner:

```bash
mvn test
```

Show: green results. Point at the structured output — pass rate, run count, evaluation scores per
assertion.

*Narration*: "Before opening a PR, the developer runs the agentic test methods locally. This is
the pro-code gate. The execution is non-deterministic, but the verdict is deterministic: pass or
fail, with a statistical threshold."

---

### Surface 2: Camunda Modeler — Test tab

**Step 3: Open the Test tab**

Switch to Camunda Modeler. Navigate to the Test tab for the claims-processing-agent process.

The generated test files from Step 1 are present in `src/main/resources/` and visible in the tab.

*Narration*: "The same files. A business analyst or low-code developer can now run and modify
these without touching Java. This is what Play became: a dedicated QA surface."

---

**Step 4: Add a segment test**

In the Test tab, add one segment test manually targeting the assessment agent sub-process.

Run the full suite. Show green across all segments. Point at the assertion results: expected
behavioral output vs. actual, structured pass/fail per segment.

*Narration*: "Each segment is independent and reusable. You are not running the whole process
every time — you are targeting what changed."

---

**Step 5: Introduce a regression**

In the BPMN connector config for the assessment agent, change the LLM model from Opus to Haiku.
Save. Re-run the test suite in the Test tab.

Show: one or more assertion failures. Point at:
- Which segment failed
- Expected behavioral output (Opus-level policy interpretation)
- Actual behavioral output (degraded Haiku response)

*Narration*: "A well-meaning engineer cuts model cost before a release — something a team
actually does. The test caught a quality regression. The agent's policy interpretation changed.
This is what you know before production, not after an incident."

---

### Surface 1 again: CI/CD gate

**Step 6: Create a PR and show the pipeline gate**

Revert the model change (or leave it — the gate should fail either way to show the mechanism).
Create a PR. Show CI picking up the test JSON from `src/main/resources/`.

Point at:
- The test files running in CI without conversion or duplication
- The pipeline failing on the same assertion that failed in Step 5

*Narration*: "The same file that runs in Web Modeler runs in the pipeline. No conversion, no
duplication. The breaking change would have been caught here too. The gate holds in CI, not
just locally."

---

**Optional: Mathieu hand-off**

After Step 6, Mathieu shows how the CPT test inputs are structured for the claims-processing-agent
specifically — evaluation hooks, conditional instructions, statistical threshold config. Technical
depth for engineering-heavy audiences.

---

## Narrative arc (full section)

> "The first question any regulated customer asks is: how do you test something non-deterministic?
>
> Here's the answer. We have a claims-processing agent — the same one you just saw escalate a
> fraud case. We generate a test suite from the BPMN in one step. We run it: green across the
> board. Deterministic pass/fail on non-deterministic AI.
>
> Now watch what happens when I introduce a realistic breaking change. A well-meaning engineer
> downgrades the model from Opus to Haiku to cut costs before a release. Re-run. Fail. The test
> caught a quality regression — the agent's policy interpretation changed in a way that matters
> for production.
>
> The same test file runs in Web Modeler and in the CI/CD pipeline. The gate holds in both places.
> You know before production. Not after."

---

## Time estimate

| Step | Surface | Time |
|------|---------|------|
| Generate tests | Claude Code | 3 min |
| Run agentic methods locally | Claude Code | 4 min |
| Open Test tab, show imported tests | Web Modeler | 2 min |
| Add segment test, run green suite | Web Modeler | 5 min |
| Opus to Haiku regression + assertion failure | Web Modeler | 5 min |
| PR creation + CI gate | Claude Code / CI | 5 min |
| Mathieu CPT input walkthrough (optional) | Claude Code | 5 min |
| **Total** | | **~24-29 min** |

---

## 8.10 scope summary (testing section)

| Epic | Confidence | Shown in demo |
|------|------------|---------------|
| Play-to-Test Transition (#3169) | 4 | Yes — Test tab is the entry point |
| Test Process Segments (#2896) | 5 | Yes — segment run in Step 4 |
| Low-Code Test Assertions (#3496) | 4 | Yes — assertion results in Steps 4 and 5 |
| Low-Code CI/CD Compatibility (#3498) | 5 | Yes — Step 6 |
| Test generation via Claude Code (#3557) | Shipped | Yes — Step 1 |
| Non-Flaky Agentic Test Execution (#3495) | 3 | No — confirm with Husna before adding |
| Low-Code Test Reports (#3497) | 3 | No — confirm with Husna before adding |
| Standalone CPT Service (#3531) | 3 | No — confirm with Husna before adding |
| Low-Code Test BPMN Coverage Gaps (#3499) | 2 | No — out of scope |

---

## Troubleshooting (testing section)

| Symptom | Cause | Fix |
|---------|-------|-----|
| `/camunda-process-test` skill not found | Skill not installed in Claude Code | Install from `~/.claude/skills/` — see setup guide |
| `mvn test` fails with connection error | No local Zeebe connection | Tests use in-memory Zeebe via CPT — check `pom.xml` dependencies |
| Test tab does not show generated files | Files not in `src/main/resources/` | Confirm skill wrote to correct path; reload Modeler |
| All assertions pass after Haiku switch | Segment not asserting on quality | Confirm assertion targets agent output text, not just process completion |
| CI does not pick up test JSON | Schema mismatch or wrong file location | Confirm files match `cpt-test-cases.schema.json` and are in `src/main/resources/` |
