# Fraud Alert Triage Agent (Event-Driven Agent)

[![Run In Camunda SaaS](https://img.shields.io/badge/Run%20In-Camunda%20SaaS-FC5D0D?style=for-the-badge)](https://modeler.cloud.camunda.io/import/resources?source=https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/event-driven-agent/models/fraud-alert-triage-agent.bpmn,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/event-driven-agent/models/fraud-alert-review.form,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/event-driven-agent/models/fraud-team-handoff.form&title=Event-Driven%20Agent)

A concrete runnable **Event-Driven Agent** example based on the pattern at [camunda.com/orchestrate/agents](https://camunda.com/orchestrate/agents/):

> Wakes up when something happens. Triggered by an event: a payment threshold breached, a document received, a timer fired, an external system signaling a state change. The agent resumes, reasons over the new context, and acts. No polling, no scheduled jobs. BPMN message, timer, and signal events are first-class constructs.

![Process Model](docs/event-driven-agent.svg)

It contains:

- a process with **no start form at all** - the only way in is an inbound webhook, so the process simply does not exist until an external system posts to it
- an agent subprocess whose first tool call deliberately takes ~8 real seconds, giving you an honest window to fire a second event at it mid-investigation
- a second, independent webhook modeled as an **interrupting message boundary event** on the agent - it can fire at any point while the agent is running and unconditionally cancels whatever the agent was doing, no matter how far along or how confident it was
- two independent ways to reach the same "confirmed fraud" outcome (a human analyst's manual review, or the interrupt event) that structurally converge on the same mandatory next steps

It is intentionally compact so you can import, run, and interrupt quickly.

## Try it in 5 minutes (Camunda 8 SaaS - recommended)

The smoothest path is to use a trial cluster in Camunda SaaS. Just use the following button and install the example into your cluster - you can sign up on the way if you don't yet have one:

[![Run In Camunda SaaS](https://img.shields.io/badge/Run%20In-Camunda%20SaaS-FC5D0D?style=for-the-badge)](https://modeler.cloud.camunda.io/import/resources?source=https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/event-driven-agent/models/fraud-alert-triage-agent.bpmn,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/event-driven-agent/models/fraud-alert-review.form,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/event-driven-agent/models/fraud-team-handoff.form&title=Event-Driven%20Agent)

Why SaaS first:

- Preconfigured to use the [Camunda-provided LLM](https://docs.camunda.io/docs/components/agentic-orchestration/camunda-provided-llm/), so no access tokens, secrets, or external API keys needed
- The [Webhook connector](https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/) is built in - deploying the process is enough to get a real, public HTTPS endpoint, with nothing else to install or configure

1. Click the button above to import the process and both forms into Camunda SaaS as one project.
2. Open **fraud-alert-triage-agent** and click **Deploy** (not "Deploy and run" - there's no start form to open, since this process only starts from a webhook).
3. Click the **Fraud alert received** start event, open the **Webhooks** tab in the properties panel, and copy its URL. It looks like `https://<region>.connectors.camunda.io/<cluster-id>/inbound/fraud-alert-received`.
4. Fire the first webhook - this is the "likely flagged" scenario (see [Demo scenarios](#demo-scenarios) below for the others):

   ```bash
   curl -X POST "<the URL you copied>" \
     -H "Content-Type: application/json" \
     -d '{
       "alertId": "ALERT-6602",
       "customerId": "CUST-40103",
       "cardLast4": "7788",
       "transactionAmount": 2450.00,
       "transactionCurrency": "USD",
       "merchantName": "Nordic Electronics Oslo",
       "merchantCountry": "Norway",
       "riskScore": 62,
       "alertReason": "First-time merchant category and country for this customer, amount well above their typical transaction size."
     }'
   ```

5. Watch the flow in Operate: a process instance appears the moment the webhook lands, and the `Fraud Investigation Agent` subprocess starts reasoning immediately - no polling, nothing waiting for the next cron tick.
6. This scenario ends up flagged, so the case lands on **Review flagged fraud alert** in Tasklist, pre-filled with the agent's own summary. Complete it choosing either decision to finish the process.

## Try the interrupt (the actual point of this example)

Steps 1-3 above are the same. This time, use the "clean-looking" scenario and interrupt it while it's still running:

1. Click the **Customer confirmed fraud** boundary event on the agent, open its **Webhooks** tab, and copy that URL too (it's a separate endpoint from the start event's).
2. Fire the **first** webhook with this payload - on paper it looks mundane, and left alone the agent would very likely clear it automatically in a few seconds:

   ```bash
   curl -X POST "<fraud-alert-received URL>" \
     -H "Content-Type: application/json" \
     -d '{
       "alertId": "ALERT-6603",
       "customerId": "CUST-40108",
       "cardLast4": "3315",
       "transactionAmount": 4800,
       "transactionCurrency": "NOK",
       "merchantName": "Alpine Ski Rentals Oslo",
       "merchantCountry": "Norway",
       "riskScore": 45,
       "alertReason": "Slightly above average ski-season purchase; first time renting from this merchant."
     }'
   ```

3. **Immediately** - within about 8 seconds, while `Cross-reference transaction history` is still running - fire the **second** webhook, correlated by the same `alertId`:

   ```bash
   curl -X POST "<fraud-confirmed URL>" \
     -H "Content-Type: application/json" \
     -d '{
       "alertId": "ALERT-6603",
       "confirmedBy": "customer-callback",
       "confirmationNotes": "Customer states they did not make this purchase and have no knowledge of the merchant.",
       "confirmedAt": "2026-07-29T10:15:00Z"
     }'
   ```

4. Watch Operate: the agent subprocess is cancelled mid-flight - you'll see it end with an interrupting boundary event, not a normal completion - and the token jumps straight to `Freeze card`, then the **Fraud team case handoff** task in Tasklist. The agent never gets to finish its investigation, and nothing about its own reasoning had any say in the outcome.

If you're not fast enough and the agent finishes on its own first, that's fine - just start a fresh instance (a new `alertId`) and try again. The 8-second delay is deliberate and documented on the `Cross-reference transaction history` tool, precisely so this is reproducible instead of a lucky race.

## What happens technically

There is no start form. The **Fraud alert received** start event is the [Webhook connector](https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/) applied to a plain start event - the process is created the instant an external system posts to it, and the entire incoming JSON body becomes the process's variables.

Inside `Fraud Investigation Agent`, the model invokes tools backed by real public services:

| # | Capability | Protocol | Public service used | Tool / BPMN element |
|---|---|---|---|---|
| 1 | Cross-reference history | REST (deliberately slow) | [httpbin.io](https://httpbin.io) `/delay/8` | `CrossReferenceTransactionHistory` |
| 2 | Convert currency | REST | [frankfurter.app](https://frankfurter.app) (ECB reference rates) | `ConvertToBaseCurrency` |

The agent's policy: cross-reference the customer's history first (always), convert to USD if the transaction isn't already in USD, then clear the alert only if there are no related alerts in the last 90 days, the monitoring system's own risk score is under 50, **and** the USD amount is under 2,000 - otherwise it holds the case for a human fraud analyst.

The event-driven part is the **Customer confirmed fraud** boundary event: a second [Webhook connector](https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/), this time applied to an *interrupting message boundary event* attached to the whole agent subprocess, correlated by `alertId`. This is what the marketing page means by "BPMN message... events are first-class constructs" - there's no polling loop checking "has the customer called back yet?", no flag the agent has to check between tool calls. Zeebe simply cancels the ad-hoc subprocess the moment the message correlates, wherever it happened to be, and the token moves on. The agent's system prompt even says so explicitly: *"there is nothing special for you to do about it"* - the guarantee is structural, not something the model has to cooperate with.

Both paths that confirm fraud - an analyst's manual review, or the interrupt - flow through a plain merging gateway (`Confirmed fraud (either path)`) into `Prepare handoff summary`, which builds one consistent explanation before `Freeze card` and the human handoff. Whichever way a case got there, the fraud team sees the same shape of information.

## Demo scenarios

All three scenarios are fired the same way - `curl` against the **Fraud alert received** webhook URL. None of them need a form.

### Likely cleared automatically

```json
{
  "alertId": "ALERT-6601",
  "customerId": "CUST-40100",
  "cardLast4": "2210",
  "transactionAmount": 145.50,
  "transactionCurrency": "USD",
  "merchantName": "Riverside Coffee Roasters",
  "merchantCountry": "United States",
  "riskScore": 38,
  "alertReason": "New merchant category for this customer, amount is modest relative to typical spend."
}
```

`CUST-40100` → 0 related alerts, risk score 38, $145.50 - clears every threshold, so the agent clears it automatically and posts to `Close alert notification`. Nothing reaches a human.

### Likely flagged for human review

```json
{
  "alertId": "ALERT-6602",
  "customerId": "CUST-40103",
  "cardLast4": "7788",
  "transactionAmount": 2450.00,
  "transactionCurrency": "USD",
  "merchantName": "Nordic Electronics Oslo",
  "merchantCountry": "Norway",
  "riskScore": 62,
  "alertReason": "First-time merchant category and country for this customer, amount well above their typical transaction size."
}
```

`CUST-40103` → 3 related alerts (`40103 mod 4 = 3`), risk score 62, $2,450 - fails every threshold, so the agent holds it for review. Confirm fraud or dismiss it in Tasklist to see both endings.

### Interrupted mid-investigation (see [above](#try-the-interrupt-the-actual-point-of-this-example))

```json
{
  "alertId": "ALERT-6603",
  "customerId": "CUST-40108",
  "cardLast4": "3315",
  "transactionAmount": 4800,
  "transactionCurrency": "NOK",
  "merchantName": "Alpine Ski Rentals Oslo",
  "merchantCountry": "Norway",
  "riskScore": 45,
  "alertReason": "Slightly above average ski-season purchase; first time renting from this merchant."
}
```

`CUST-40108` → 0 related alerts (`40108 mod 4 = 0`), risk score 45 - on its own, this one is on track to clear. Firing the `fraud-confirmed` webhook for `ALERT-6603` while it's still investigating overrides that outcome completely, regardless of how the investigation would have ended.

The `relatedAlerts90d` figure is computed deterministically from the digits in `customerId` (`modulo(number(substring(customerId, 6)), 4)`) rather than pulled from a real database, so every run of a given scenario behaves the same way.

## Local/self-managed path (advanced)

Use this only if you need local Docker-based setup with your own LLM. The webhook mechanics work the same way, but you'll need to construct the URL yourself (no **Webhooks** tab in Desktop Modeler):

`http(s)://<connectors base URL>/inbound/<webhook ID>` - `fraud-alert-received` and `fraud-confirmed` are the two webhook IDs in this model. See the [HTTP Webhook connector docs](https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/#activate-the-http-webhook-connector-by-deploying-your-diagram) for the exact base URL for your setup.

1. [Install a local LLM](https://docs.camunda.io/docs/next/guides/getting-started-agentic-orchestration/#set-up-ollama) (or use hosted credentials).
2. Configure environment variables (example for local Ollama):

```env
SECRET_CAMUNDA_PROVIDED_LLM_API_ENDPOINT=http://localhost:11434/v1
SECRET_CAMUNDA_PROVIDED_LLM_API_KEY=null
SECRET_CAMUNDA_PROVIDED_LLM_DEFAULT_MODEL=gpt-oss:20b
```

3. Start [Camunda 8 Run](https://docs.camunda.io/docs/self-managed/quickstart/developer-quickstart/c8run/).
4. Deploy [models/fraud-alert-triage-agent.bpmn](models/fraud-alert-triage-agent.bpmn), [models/fraud-alert-review.form](models/fraud-alert-review.form), and [models/fraud-team-handoff.form](models/fraud-team-handoff.form).
5. Trigger it with the same `curl` commands shown above, against your own webhook base URL.

Camunda secrets read `secrets.<NAME>` from same-named environment variables.

## Notes and disclaimer

This is an illustrative demo only.

- Public services are stand-ins for real enterprise systems (a card processor, a case-management platform).
- The 8-second delay on `Cross-reference transaction history` exists purely so the interrupt demo is reproducible in a live walkthrough - a real fraud platform's history lookup wouldn't need to be artificially slowed down.
- No real customer, card, or transaction data is used.
- Nothing here should be interpreted as fraud-detection or compliance guidance.

Please be respectful of shared public services (httpbin.io, frankfurter.app). This example is intentionally low-volume and not intended for load testing.
