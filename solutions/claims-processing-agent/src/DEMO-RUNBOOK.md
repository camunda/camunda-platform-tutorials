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
