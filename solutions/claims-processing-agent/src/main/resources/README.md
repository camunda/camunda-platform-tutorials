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
- Deterministic assertions on agentic behavior, played back in the Web Modeler Play tab (low-code)
- Regression detection before production (a system-prompt change that degrades agent quality)
- CI/CD gate on the same test artifacts (pipeline portability)

Two surfaces: Claude Code for generation and pro-code execution; the Web Modeler Play tab for
low-code playback of the end-to-end scenarios.

---

## Pre-flight checklist (testing section)

In addition to the cluster prerequisites above:

- [ ] Claude Code installed with the `/camunda-process-test` skill available (Camunda Process Test 8.10.0-SNAPSHOT)
- [ ] Java 21 + Maven installed locally
- [ ] `solutions/claims-processing-agent/` opened in Claude Code terminal
- [ ] Web Modeler open on the process: [claims-processing-agent](https://modeler.camunda.io/diagrams/cd448596-a3d9-4a7f-b0bf-e99d41f51d53--claims-processing-agent?v=0,0,1)
- [ ] Play tab visible in Web Modeler (8.10 snapshot build)

---

## Click path

### Surface 1: Claude Code — generate and validate

**Step 1: Generate test cases from the BPMN**

Open a terminal in `solutions/claims-processing-agent/`. Run:

```
/camunda-process-test
```

Point the skill at `src/main/resources/claims-processing-agent.bpmn` **and** at the
`Testing & Acceptance Criteria` section of this README. The test plan is the context the skill
generates against: it gives the skill the requirements, the expected outcomes, and the scenario
catalogue, so the generated suite maps to documented criteria rather than guessing.

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

### Surface 2: Web Modeler — Play tab

**Step 3: Sync the process application, then open the Play tab**

First sync the process application so Web Modeler picks up the generated test artifacts. Show
GitHub Desktop, review the changed files, and move them over (commit and push) so Web Modeler's
Git sync pulls them in. Then switch to Web Modeler and open the Play tab for the
claims-processing-agent process.

The end-to-end scenarios from [`claims-processing-agent test scenarios.json`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/src/main/resources/claims-processing-agent%20test%20scenarios.json) are now present and runnable in the Play tab.

*Narration*: "The same scenarios. A business analyst or low-code developer can now run them without
touching Java. This is what Play became: a dedicated QA surface."

---

**Step 4: Introduce a regression**

Open the assessment agent's system prompt in the BPMN connector config. Weaken it: remove the
instruction that requires the agent to enumerate specific fraud indicators and justify its
decision. Save, sync, and re-run the end-to-end scenarios.

Show: one or more quality assertions fail. Point at:
- Which scenario failed
- The LLM-as-judge feedback explaining why the report no longer meets the bar (missing fraud
  enumeration, weaker justification)
- That routing can still be correct: the regression is in quality, which the judge catches

*Narration*: "An engineer simplifies the system prompt before a release — something a team
actually does. Routing still works, so a shallow test passes. But the agentic quality gate catches
it: the report stopped enumerating fraud indicators. This is what you know before production, not
after an incident."

---

### Surface 1 again: CI/CD gate

**Step 5: Create a PR and show the pipeline gate**

Revert the prompt change (or leave it — the gate should fail either way to show the mechanism).
Create a PR. Show CI picking up the test JSON from `src/main/resources/`.

Point at:
- The test files running in CI without conversion or duplication
- The pipeline failing on the same assertion that failed in Step 4

*Narration*: "The same file that runs in Web Modeler runs in the pipeline. No conversion, no
duplication. The breaking change would have been caught here too. The gate holds in CI, not
just locally."

---

**Optional: go deeper into the code**

For engineering-heavy audiences, open the test sources directly and walk the structure: the CPT
instruction JSON, the LLM-as-judge assertions, and the integration profile. The
`Testing & Acceptance Criteria` section below documents how each requirement maps to a test, and
the `Where the tests live` table deep-links every artifact.

---

## Narrative arc (full section)

> "The first question any regulated customer asks is: how do you test something non-deterministic?
>
> Here's the answer. We have a claims-processing agent — the same one you just saw escalate a
> fraud case. We generate a test suite from the BPMN in one step. We run it: green across the
> board. Deterministic pass/fail on non-deterministic AI.
>
> Now watch what happens when I introduce a realistic breaking change. An engineer simplifies the
> assessment agent's system prompt before a release, dropping the instruction to enumerate fraud
> indicators. Routing still works, so a shallow test passes. Re-run the quality gate. Fail. The
> LLM-as-judge caught a quality regression: the report stopped documenting the fraud signals in a
> way that matters for production.
>
> The same test file runs in Web Modeler and in the CI/CD pipeline. The gate holds in both places.
> You know before production. Not after."

---

## Time estimate

Estimates below are unconfirmed and need a dry-run to validate.

| Step | Surface | Time |
|------|---------|------|
| Generate tests | Claude Code | 3 min |
| Run agentic methods locally | Claude Code | 4 min |
| Sync app, open Play tab, run scenarios | Web Modeler | 4 min |
| System-prompt regression + quality-assertion failure | Web Modeler | 5 min |
| PR creation + CI gate | Claude Code / CI | 5 min |
| **Total** | | **~21 min (unconfirmed)** |

---

## 8.10 scope summary (testing section)

| Epic | Confidence | Shown in demo |
|------|------------|---------------|
| Play-to-Test Transition (#3169) | 4 | Yes — Play tab is the entry point |
| Test Process Segments (#2896) | 5 | Separate demo — Dominic, on the Test Studio internal prototype |
| Low-Code Test Assertions (#3496) | 4 | Separate demo — Dominic, on the Test Studio internal prototype |
| Low-Code CI/CD Compatibility (#3498) | 5 | Yes — Step 5 |
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
| Play tab does not show generated scenarios | Process application not synced | Sync via GitHub Desktop so Web Modeler pulls the files; confirm they are in `src/main/resources/` |
| All assertions pass after the prompt change | Assertion not gating on quality | Confirm the LLM-as-judge assertion targets the agent report text, not just process completion |
| CI does not pick up test JSON | Schema mismatch or wrong file location | Confirm files match `cpt-test-cases.schema.json` and are in `src/main/resources/` |

---

---

# Testing & Acceptance Criteria

This section documents what the test suite guarantees and which layer proves each requirement. The process is non-deterministic at runtime, the verdict is deterministic: every requirement below maps to a test that passes or fails.

Use this section as the brief before generating the suite. It is the context to hand the `/camunda-process-test` skill in Step 1, and the material to narrate while the skill runs: the requirements, the expected outcomes, and the scenario catalogue.

The requirements are high-level and illustrative. They describe a fictional claims-processing policy for Camunda Insurance so the test intent is readable without the BPMN open.

**Assertion philosophy.** Every assertion exists to prove a business requirement, not to pad coverage. A test asserts the path its requirement names (the elements completed through to the terminal end event), the routing variable (`claimDecision`), and — for the judge — that its quality outputs are populated. "The process finished" is never sufficient on its own.

## How the process decides

Two AI roles, separate concerns:

- The **assessment agent** (`Agent_ClaimsAssessment`, ad-hoc sub-process) gathers evidence through tools and returns a structured report. Its `decision` field (`APPROVE`, `MANUAL_REVIEW`, `ESCALATE`) maps to `claimDecision` and drives the gateway.
- The **quality judge** (`Agent_Judge`, displayed as `Quality Judge`) is an independent LLM-as-judge. It does not route the claim. It scores the assessment's quality (`agentQualityScore`, `qualityFeedback`) so a degraded model is visible before production.

## Three test layers, three guarantees

| Layer | Question it answers | External systems | Acceptance signal | Run |
|-------------------------|------------------------------------------------|------------------------------|------------------------------------------------------------|----------------------------------------------|
| **Process tests**       | Does the workflow route every claim correctly? | Mocked (canned agent and judge output, mocked tool jobs) | 100% BPMN element and sequence-flow reachability           | `mvn test`                                   |
| **Segment integration tests** | Does each external system behave correctly on its own? | Real, one at a time          | Every agent, connector, and service task exercised; quality gated by LLM-as-judge and semantic similarity | `mvn test -P integration-test` |
| **Process integration tests** | Does the whole thing work for real?            | Real, all together           | Realistic varied-data scenarios reach the correct outcome | `mvn test -P integration-test`; the end-to-end scenarios also play back in the Web Modeler Play tab |

All three layers run locally and in CI. The process tests are the commit gate and run on every push via [`.github/workflows/test-suites.yml`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/.github/workflows/test-suites.yml) (`mvn test`). The segment integration and process integration tests run under the `integration-test` profile and need Docker, the Connectors runtime, and AWS Bedrock credentials (the repo-root `.env` locally, the same values as CI secrets). The end-to-end scenarios additionally play back in the Web Modeler Play tab from [`claims-processing-agent test scenarios.json`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/src/main/resources/claims-processing-agent%20test%20scenarios.json).

## Requirements to test layer

### Process requirements (PR) — proven by the process tests

| ID | Requirement | Verified by | Comment |
|------|----------------------------------------------------------------------------|--------------------------------------------------|---------|
| PR-1 | Every claim reaches exactly one terminal state. No instance stalls.        | 100% coverage across all 6 scenarios             | — |
| PR-2 | A clean low-risk claim is approved without human touch.                    | `clean claim — agent approves`                   | — |
| PR-3 | An ambiguous claim with no clear fraud signal goes to manual review.       | `borderline claim — agent routes to manual review` | — |
| PR-4 | A claim with fraud indicators is escalated to a human adjuster.            | `high-fraud claim — agent escalates`             | — |
| PR-5 | Missing documents trigger a request, then assessment resumes.              | `agent requests documents`                       | — |
| PR-6 | The agent can escalate a claim it cannot resolve, mid-assessment.          | `agent cannot complete — EscalateToHuman`        | — |
| PR-7 | An assessment-agent failure routes to a supervisor and never drops the claim. | `AHSP throws error — handled by supervisor`   | — |

### Segment integration requirements (SIR) — proven by the segment integration tests

| ID | Requirement | Verified by | Comment |
|------|----------------------------------------------------------------------------|--------------------------------------------------|-----------|
| SIR-1 | PolicyLookup returns status, coverage, deductible, and fraud-risk score.   | [`ClaimsExternalSystemsIT.policyLookupInIsolation`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) | — |
| SIR-2 | GetCustomerProfile returns tier, account standing, and fraud history.      | [`ClaimsExternalSystemsIT.getCustomerProfileInIsolation`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) | — |
| SIR-3 | CalculateDamageEstimate returns an amount, category, and anomaly flags.    | [`ClaimsExternalSystemsIT.calculateDamageEstimateInIsolation`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) | — |
| SIR-4 | The assessment report identifies fraud risk and recommends a decision. | [`ClaimsExternalSystemsIT.assessmentReportIdentifiesFraud`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) (LLM-as-judge); [`assessmentReportSemanticSimilarity`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) (semantic similarity, skipped) | Judge passes; similarity skipped — needs Bedrock embedding access (403) |
| SIR-5 | The Quality Judge populates every quality output it is prompted to produce (overall score in [0,1], non-empty feedback, full score object). | [`ClaimsExternalSystemsIT.judgePopulatesQualityOutputs`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) (heavy variable validation) | Depends on the Agent_Judge JSON fix |
| SIR-6 | Each tool builds its request from the supplied claim or customer identifier. | [`ClaimsExternalSystemsIT`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) connector segments | — |

SIR-4 is proven two ways. An LLM-as-judge assertion (`hasVariableSatisfiesJudge` on `assessmentReport`) verifies the report identifies fraud risk and recommends a decision — this passes. A native semantic-similarity assertion (`hasVariableSimilarTo("assessmentReport", <reference>)`, backed by a Bedrock Titan embedding model, default threshold 0.5) is wired and compiles but is currently skipped: the test IAM user lacks `bedrock:InvokeModel` on `amazon.titan-embed-text-v2:0` (403 AccessDenied). Grant that permission — or point `camunda.process-test.similarity.embedding-model` at another provider — and remove the `@Disabled` to turn it green. Semantic-similarity assertions require CPT 8.10 (shipped 8.10.0-alpha1); the harness is pinned to `8.10.0-SNAPSHOT`.

### Process integration requirements (PIR) — proven by the process integration tests

| ID | Requirement | Verified by | Comment |
|-------|----------------------------------------------------------------------------|--------------------------------------------------|-----------|
| PIR-1 | A fraudulent claim is escalated to a human adjuster (full path to `End_HumanResolved`). | [`ClaimsProcessingAgentIT.fraudClaimEscalatesToAdjuster`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsProcessingAgentIT.java) | Asserts full path + `claimDecision=ESCALATE`. Uses the known fraud id `CLM-2025-0042`. |
| PIR-2 | A clean claim is approved without human touch (full path to `End_ClaimApproved`). | [`ClaimsProcessingAgentIT.cleanClaimIsApproved`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsProcessingAgentIT.java) | Disabled pending beeceptor fixture — see [`test/BEECEPTOR-FIXTURES.md`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/BEECEPTOR-FIXTURES.md) |
| PIR-3 | An ambiguous claim goes to manual review (full path to `End_ManualResolved`). | [`ClaimsProcessingAgentIT.borderlineClaimGoesToManualReview`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsProcessingAgentIT.java) | Disabled pending beeceptor fixture |

The `Comment` column flags anything outstanding: an em-dash means the test passes; `Disabled` marks a test written to spec but gated on an external dependency. Test reports: every layer writes JUnit XML to `target/surefire-reports/`; the process layer also writes the CPT coverage report to `target/coverage-report/report.json` (HTML is skipped on the pinned 8.10-SNAPSHOT, see below).

### Coverage thresholds (declared, gated)

| Layer | Declared target | Machine gate | Note |
|-------|-----------------|--------------|------|
| Process | 100% | ≥ 90% | 100% on the stable 8.9.x line; the 8.10.0-SNAPSHOT report tooling under-counts to ~93%, so the gate floor is 90%. |
| Process integration | 40% | ≥ 40% | With PIR-2/PIR-3 disabled (beeceptor fixtures pending) and the error path unreachable live. Re-enabling PIR-2/3 raises it toward 60%. |

The gate is enforced by the report generator (reads `report.json`, exits non-zero if below). The camunda-process-test skill should respect these per-layer thresholds.

### Optional coverage extensions (not built)

Not needed for the demo, but available to raise process-integration coverage once beeceptor fixtures exist: a documents-request scenario (`RequestAdditionalDocuments`), an agent self-escalation scenario (`EscalateToHuman`), and varied claim types (collision/theft/flood/liability).

### Reports — two options

- **Option A (ideal, requirement-aligned):** `bash test/report/run-all.sh` runs both layers, stashes each run's surefire + `report.json`, gates coverage, and generates `test/target/unified-report.html` — the three category sections, each requirement with its status, a skipped callout, coverage bands, and bpmn-js diagrams highlighting the covered path (offline, vendored bpmn-js).
- **Option B (config-only, CPT-native):** the stock CPT coverage report grouped by the requirement-named tests. On 8.10-SNAPSHOT its HTML viewer is broken (missing bundled assets); the diagram renders once the `definitions[id] = processModels[].xml` data-shape fix is applied to the existing `static/` viewer. It shows coverage grouped by test, not the full requirement matrix — that is Option A.
### End-to-end scenario catalogue

Each scenario feeds realistic claim data to the live agents and asserts the outcome a human would expect. The agent makes the routing call, so these scenarios verify that real model behavior matches policy, not just that the wiring traverses. Source: [`claims-processing-agent test scenarios.json`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/src/main/resources/claims-processing-agent%20test%20scenarios.json).

| # | Scenario | Expected outcome | Terminal element | Requirement |
|---|------------------------------------------------------------------------------|----------------------------------------------------------------|------------------------|---------|
| 1 | Well-documented €500 parking-lot collision, active policy, clean history      | Automatically approved, no human touch                         | `End_ClaimApproved`    | PR-2    |
| 2 | Poorly-documented €5,000 flood claim, no photos or repair proof attached      | Returned to the customer for the missing documentation, then assessed once received | `RequestAdditionalDocuments` | PR-5    |
| 3 | €48,000 total-loss claim, collision coverage added 8 days before the incident, prior fraud investigation | Escalated to a human adjuster as suspected fraud               | `Task_HumanReview`     | PR-4    |
| 4 | Ambiguous €12,000 overnight theft, no witnesses, medium risk, no hard fraud signal | Sent to an adjuster for manual review                          | `Task_ManualReview`    | PR-3    |
| 5 | €3,000 liability claim where the assessment agent connector times out         | Assessment failure caught and handed to a supervisor, claim re-queued | `Task_HandleError`     | PR-7    |

Scenarios 1 to 4 exercise the four agent-driven routing outcomes; scenario 5 exercises the failure path independent of the model. The `Requirement` column links each scenario back to the routing requirement it realizes with live data: the process tests prove the requirement deterministically with mocked output, and the matching scenario proves the same outcome holds under the real model. Routing in scenarios 1 to 4 depends on live model judgment, so the tests assert the expected terminal element with a retry window and treat a borderline model call (for example scenario 4 landing on escalation instead of manual review) as a quality signal to investigate, not an immediate failure.

**Web Modeler playback.** Scenarios 1 to 4 are saved as live-replay scenarios in [`claims-processing-agent test scenarios.json`](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/src/main/resources/claims-processing-agent%20test%20scenarios.json) for the Web Modeler Play tab: each creates the instance, lets the real agents run on the cluster, completes the human step the outcome requires, and asserts completion. The fifth replay scenario is an agent-initiated escalation (PR-6), which is a real model outcome. Scenario 5 in the table above (a forced connector failure, PR-7) cannot be reproduced by live replay and stays in the process-test layer only. Live replay depends on the `claim-demo.free.beeceptor.com` mock returning data consistent with each claim id, so the agent reaches the intended branch.

## Where the tests live

Web Modeler does not track the test source paths, so these deep-link to the repository (`HanselIdes/camunda-8-tutorials`, branch `demo`).

| Artifact | Location |
|------------------------------|----------|
| Process test scenarios (mocked) | [test-cases/CamundaInsurance_ClaimsProcessing.test.json](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/resources/test-cases/CamundaInsurance_ClaimsProcessing.test.json) |
| Web Modeler playback scenarios | [claims-processing-agent test scenarios.json](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/src/main/resources/claims-processing-agent%20test%20scenarios.json) |
| Process test runner          | [ProcessTest.java](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ProcessTest.java) |
| Integration A — isolation IT | [ClaimsExternalSystemsIT.java](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) |
| Integration A — tools harness BPMN | [test-claims-tools.bpmn](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/resources/bpmn/test-claims-tools.bpmn) |
| Integration B — end-to-end IT | [ClaimsProcessingAgentIT.java](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsProcessingAgentIT.java) |
| Integration profile config   | [application-integration.yml](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/src/test/resources/application-integration.yml) |
| Test harness build           | [test/pom.xml](https://github.com/HanselIdes/camunda-8-tutorials/blob/demo/solutions/claims-processing-agent/test/pom.xml) |

## Refreshing mock data after a model swap

The process tests mock the agent and judge with canned output. When the model or its prompts change, that output drifts and the mocks go stale. Both integration tests log a `CAPTURE` line with the live `agent` variable after each run. To refresh:

1. Run an integration test against the new model (`mvn test -P integration-test`).
2. Copy the captured `agent` JSON from the log into the matching scenario's `COMPLETE_JOB_AD_HOC_SUB_PROCESS` block in the process test file.
3. Re-run `mvn test` and confirm the routing assertions still hold.

This keeps the fast mocked gate honest against what the real model now produces.

## Running each layer

**Process tests (commit gate):**
```bash
cd solutions/claims-processing-agent/test
mvn test
```
Output: `Tests run: 6, Failures: 0`. Coverage is 100% of BPMN elements and sequence flows (the six scenarios are unchanged from the 8.9.x line that reported 100%). Note: on `8.10.0-SNAPSHOT` the HTML coverage-report generator can throw `Report resources not found` and skip `target/coverage-report/report.html`; the tests still pass and coverage is confirmed from the scenarios. This resolves when the harness moves to a stable 8.10 release.

**Integration A and B (on demand):**
```bash
cd solutions/claims-processing-agent/test
env $(cat ../../../.env | grep -v '^#' | xargs) mvn test -P integration-test
```
Requires Docker running, a filled `.env` (AWS Bedrock keys), and a judge provider key for the quality assertions. The agents call real Bedrock; the service tools call `claim-demo.free.beeceptor.com`.

## What the tests do not assert

Reachability and routing, not data values. A process test never asserts the exact dollar amount a service task returns, only that the claim reached the element the rule selected. The one exception is the agentic quality gate: the judge and the LLM-as-judge assertions evaluate the meaning of the agent's report, because the report is the unit under test when the model or its prompt changes.

The integration tests do not assert side effects on external systems. A real connector call is exercised, but downstream state in those systems is out of scope. There is no testing of form UI either. External side effects and form rendering are always mocked or out of scope across every layer.
