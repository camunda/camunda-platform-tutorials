# Claims Processing Agent (8.10)

An insurance-claims solution built on Camunda 8.10's agentic orchestration. An assessment agent runs tool calls to validate policy coverage, customer risk profile, and damage estimate, then writes a structured report. An independent LLM quality judge scores the report and routes the claim: approve automatically, escalate to a human adjuster, or send to manual review.

**What it shows:**
- Multi-tool-call agent with visible reasoning (`<thinking>` traces, conversation history, token usage)
- Agent visibility in Operate's 8.10 agent view
- LLM-as-judge as a quality gate independent from routing
- Human-task hand-off with the full assessment surfaced to the adjuster

---

## Prerequisites

Your SaaS cluster needs these connector secrets configured before deploy:

| Secret | Purpose |
|--------|---------|
| `AWS_BEDROCK_REGION` | Bedrock region |
| `AWS_BEDROCK_ACCESS_KEY` | IAM access key |
| `AWS_BEDROCK_SECRET_KEY` | IAM secret key |
| `AWS_BEDROCK_MODEL` | Model ID or inference profile ARN |

---

## Deploy

### Configure c8ctl profile (one-time)

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
cd solutions/claims-processing-agent
c8ctl deploy . --profile=<your-profile-name>
```

Expected output: 6 resources deployed (1 BPMN + 5 forms), process ID `CamundaInsurance_ClaimsProcessing`.

---

# Testing & Acceptance Criteria

This section documents what the test suite guarantees and which layer proves each requirement. The process is non-deterministic at runtime; the verdict is deterministic: every requirement below maps to a test that passes or fails.

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
| **Process integration tests** | Does the whole thing work for real?            | Real, all together           | Realistic varied-data scenarios reach the correct outcome | `mvn test -P integration-test` |

All three layers run locally and in CI. The process tests are the commit gate and run on every push via [`.github/workflows/test-suites.yml`](https://github.com/camunda/camunda-8-tutorials/blob/main/.github/workflows/test-suites.yml) (`mvn test`). The segment integration and process integration tests run under the `integration-test` profile and need Docker, the Connectors runtime, and AWS Bedrock credentials (the repo-root `.env` locally, the same values as CI secrets).

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
| SIR-1 | PolicyLookup returns status, coverage, deductible, and fraud-risk score.   | [`ClaimsExternalSystemsIT.policyLookupInIsolation`](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) | — |
| SIR-2 | GetCustomerProfile returns tier, account standing, and fraud history.      | [`ClaimsExternalSystemsIT.getCustomerProfileInIsolation`](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) | — |
| SIR-3 | CalculateDamageEstimate returns an amount, category, and anomaly flags.    | [`ClaimsExternalSystemsIT.calculateDamageEstimateInIsolation`](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) | — |
| SIR-4 | The assessment report identifies fraud risk and recommends a decision. | [`ClaimsExternalSystemsIT.assessmentReportIdentifiesFraud`](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) (LLM-as-judge); [`assessmentReportSemanticSimilarity`](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) (semantic similarity, errors) | Judge passes; similarity errors with 403 — IAM user lacks `bedrock:InvokeModel` on `amazon.titan-embed-text-v2:0` |
| SIR-5 | The Quality Judge populates every quality output it is prompted to produce (overall score in [0,1], non-empty feedback, full score object). | [`ClaimsExternalSystemsIT.judgePopulatesQualityOutputs`](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) | — |
| SIR-6 | Each tool builds its request from the supplied claim or customer identifier. | [`ClaimsExternalSystemsIT`](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) connector segments | — |

SIR-4 is proven two ways. An LLM-as-judge assertion (`hasVariableSatisfiesJudge` on `assessmentReport`) verifies the report identifies fraud risk and recommends a decision — this passes. A native semantic-similarity assertion (`hasVariableSimilarTo("assessmentReport", <reference>)`, backed by a Bedrock Titan embedding model, default threshold 0.5) is wired but currently errors with a 403: the test IAM user lacks `bedrock:InvokeModel` on `amazon.titan-embed-text-v2:0`. Grant that permission — or point `camunda.process-test.similarity.embedding-model` at another provider — to turn it green. Semantic-similarity assertions require CPT 8.10 (shipped 8.10.0-alpha1); the harness is pinned to `8.10.0-SNAPSHOT`.

### Process integration requirements (PIR) — proven by the process integration tests

| ID | Requirement | Verified by | Comment |
|-------|----------------------------------------------------------------------------|--------------------------------------------------|-----------|
| PIR-1 | A fraudulent claim is escalated to a human adjuster (full path to `End_HumanResolved`). | [`ClaimsProcessingAgentIT.fraudClaimEscalatesToAdjuster`](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsProcessingAgentIT.java) | Asserts full path + `claimDecision=ESCALATE`. Uses fraud fixture `CLM-2025-0042`. |
| PIR-2 | A clean claim is approved without human touch (full path to `End_ClaimApproved`). | [`ClaimsProcessingAgentIT.cleanClaimIsApproved`](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsProcessingAgentIT.java) | Uses clean fixture `CLM-IT-CLEAN-001`. See [`test/BEECEPTOR-FIXTURES.md`](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/BEECEPTOR-FIXTURES.md). |
| PIR-3 | An ambiguous claim goes to manual review (full path to `End_ManualResolved`). | [`ClaimsProcessingAgentIT.borderlineClaimGoesToManualReview`](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsProcessingAgentIT.java) | Uses border fixture `CLM-IT-BORDER-001`. |

The `Comment` column flags anything outstanding: an em-dash means the test passes cleanly. Test reports: every layer writes JUnit XML to `target/surefire-reports/`; the process layer also writes the CPT coverage report to `target/coverage-report/report.json`.

### Coverage thresholds (declared, gated)

| Layer | Declared target | Machine gate | Note |
|-------|-----------------|--------------|------|
| Process | 100% | ≥ 90% | 100% on the stable 8.9.x line; the 8.10.0-SNAPSHOT report tooling under-counts to ~93%, so the gate floor is 90%. |
| Process integration | 40% | ≥ 40% | With error paths unreachable live. |

The gate is enforced by the report generator (reads `report.json`, exits non-zero if below).

### Optional coverage extensions

Available to raise process-integration coverage once beeceptor fixtures exist: a documents-request scenario (`RequestAdditionalDocuments`), an agent self-escalation scenario (`EscalateToHuman`), and varied claim types (collision/theft/flood/liability).

### Reports

`bash test/report/run-all.sh` runs both layers, stashes each run's surefire + `report.json`, gates coverage, and generates `test/target/unified-report.html` — the three category sections, each requirement with its status, a skipped callout, coverage bands, and bpmn-js diagrams highlighting the covered path (offline, vendored bpmn-js).

## Where the tests live

| Artifact | Location |
|------------------------------|----------|
| Process test scenarios (mocked) | [test-cases/CamundaInsurance_ClaimsProcessing.test.json](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/resources/test-cases/CamundaInsurance_ClaimsProcessing.test.json) |
| Process test runner          | [ProcessTest.java](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ProcessTest.java) |
| Integration A — isolation IT | [ClaimsExternalSystemsIT.java](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsExternalSystemsIT.java) |
| Integration A — tools harness BPMN | [test-claims-tools.bpmn](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/resources/bpmn/test-claims-tools.bpmn) |
| Integration B — end-to-end IT | [ClaimsProcessingAgentIT.java](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/java/io/camunda/tests/ClaimsProcessingAgentIT.java) |
| Integration profile config   | [application-integration.yml](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/src/test/resources/application-integration.yml) |
| Test harness build           | [test/pom.xml](https://github.com/camunda/camunda-8-tutorials/blob/main/solutions/claims-processing-agent/test/pom.xml) |

## Running each layer

**Process tests (commit gate):**
```bash
cd solutions/claims-processing-agent/test
mvn test
```
Output: `Tests run: 6, Failures: 0`. Coverage is 100% of BPMN elements and sequence flows. Note: on `8.10.0-SNAPSHOT` the HTML coverage-report generator can throw `Report resources not found` and skip `target/coverage-report/report.html`; the tests still pass. This resolves when the harness moves to a stable 8.10 release.

**Integration tests (on demand):**
```bash
cd solutions/claims-processing-agent/test
env $(cat ../../../.env | grep -v '^#' | xargs) mvn test -P integration-test
```
Requires Docker running and a filled `.env` (AWS Bedrock keys). The agents call real Bedrock; the service tools call `claim-demo.free.beeceptor.com`.

**All layers with unified report:**
```bash
cd solutions/claims-processing-agent/test
bash report/run-all.sh
```

## Refreshing mock data after a model swap

The process tests mock the agent and judge with canned output. When the model or its prompts change, that output drifts and the mocks go stale. Both integration tests log a `CAPTURE` line with the live `agent` variable after each run. To refresh:

1. Run an integration test against the new model (`mvn test -P integration-test`).
2. Copy the captured `agent` JSON from the log into the matching scenario's `COMPLETE_JOB_AD_HOC_SUB_PROCESS` block in the process test file.
3. Re-run `mvn test` and confirm the routing assertions still hold.

This keeps the fast mocked gate honest against what the real model now produces.

## What the tests do not assert

Reachability and routing, not data values. A process test never asserts the exact dollar amount a service task returns, only that the claim reached the element the rule selected. The one exception is the agentic quality gate: the judge and the LLM-as-judge assertions evaluate the meaning of the agent's report, because the report is the unit under test when the model or its prompt changes.

The integration tests do not assert side effects on external systems. A real connector call is exercised, but downstream state in those systems is out of scope. There is no testing of form UI either.
