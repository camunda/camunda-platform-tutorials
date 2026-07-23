# Bank Support Orchestrator Agent

[![Run In Camunda SaaS](https://img.shields.io/badge/Run%20In-Camunda%20SaaS-FC5D0D?style=for-the-badge)](https://modeler.cloud.camunda.io/import/resources?source=https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/orchestrator-agent/models/bank-support-orchestrator.bpmn,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/orchestrator-agent/models/bank-support-loan-agent.bpmn,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/orchestrator-agent/models/bank-support-account-agent.bpmn,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/orchestrator-agent/models/bank-support-card-agent.bpmn,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/orchestrator-agent/models/bank-support-request.form,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/orchestrator-agent/models/bank-support-review.form&title=Bank%20Support%20Orchestrator)

A concrete runnable **Orchestrator Agent** example based on the pattern at [camunda.com/orchestrate/agents](https://camunda.com/orchestrate/agents/).

It contains:

- one orchestrator agent that reasons about which specialist agent(s) to invoke, in what order, and with what context
- three specialist sub-agents (loan, account, card), each a separate deployable process with its own real tool call
- delegation modeled as Call Activities, so every specialist runs as its own, independently visible process instance
- deterministic aggregation and routing after the orchestrator finishes, with human handoff for anything a specialist couldn't resolve

It is intentionally compact so you can import, run, and inspect quickly.

Based on a more rich demo done at CamundaCon 2025: https://github.com/berndruecker/bank-support-agent-demo

## Try it in 5 minutes (Camunda 8 SaaS - recommended)

The smoothest path is to use a trial cluster in Camunda SaaS. Just use the following button and install the example into your cluster - you can signup on the way if you don't yet have one:

[![Run In Camunda SaaS](https://img.shields.io/badge/Run%20In-Camunda%20SaaS-FC5D0D?style=for-the-badge)](https://modeler.cloud.camunda.io/import/resources?source=https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/orchestrator-agent/models/bank-support-orchestrator.bpmn,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/orchestrator-agent/models/bank-support-loan-agent.bpmn,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/orchestrator-agent/models/bank-support-account-agent.bpmn,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/orchestrator-agent/models/bank-support-card-agent.bpmn,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/orchestrator-agent/models/bank-support-request.form,https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/examples/orchestrator-agent/models/bank-support-review.form&title=Bank%20Support%20Orchestrator)

Why SaaS first:

- Preconfigured to use the [Camunda-provided LLM](https://docs.camunda.io/docs/components/agentic-orchestration/camunda-provided-llm/), so no access tokens, secrets, or external API keys needed
- No local installation required

1. Click the button above to import all four processes and both forms into Camunda SaaS as one project.
2. In the project view, select all four `.bpmn` files (the orchestrator, plus the loan, account, and card sub-agent processes) and deploy them together - the orchestrator's Call Activities won't find their specialist processes until those are deployed too.
3. Open **bank-support-orchestrator** and click **Deploy and Run** (or start a new instance if it's already deployed). Its start form opens - pick a **Scenario** from the dropdown, and leave **Customer request** blank to run it as-is. Or write your own request - whatever you type there takes priority over the dropdown.

   (Starting via API/`zbctl` instead of the form works the same way - just supply `customerRequest` directly, e.g. the "mixed" sample:
   ```json
   {
     "customerRequest": "Please check whether DE89370400440532013000 is a valid IBAN for my transfer, and also tell me what my monthly payment would look like on a $200,000 loan at 6% over 30 years."
   }
   ```
   )
4. Watch the flow in Operate: the orchestrator delegates to whichever specialist(s) are relevant - each spins up as its own child process instance - then a `PrepareCaseSummary` script task and an `All resolved?` gateway route deterministically to an automatic customer notification or a human review task.
5. If the case was escalated, complete the **Review escalated case** task in Tasklist to finish the process.

## What happens technically

The orchestrator's ad-hoc subprocess exposes three specialist agents as Call Activity tools. The LLM decides which one(s) to invoke based on the customer's message - a single request can trigger more than one specialist at once.

| # | Specialist (separate process) | Real tool call | Public service used |
|---|---|---|---|
| 1 | `bank-support-loan-agent` | `CalculateLoanPayment` | [api.mathjs.org](https://api.mathjs.org) expression evaluator (monthly payment formula) |
| 2 | `bank-support-account-agent` | `ValidateIban` | [openiban.com](https://openiban.com) IBAN structure/checksum validation |
| 3 | `bank-support-card-agent` | `LookupCardBin` | [lookup.binlist.net](https://binlist.net) card bank-identification-number lookup |

Each specialist is its own BPMN process: `Start -> AI Agent ad-hoc subprocess (one real tool + a RecordXResolution script task) -> End`. The script task captures the agent's own conclusion as structured data (`{ status, summary }`) - `resolved` or `needs-human` - which becomes the process's output. Because delegation is modeled as a Call Activity rather than a nested ad-hoc subprocess, every specialist that runs shows up as its own, separately inspectable process instance in Operate - exactly the "specialist agents, each operating in their own subprocess" pattern the Orchestrator Agent is built around.

After the orchestrator's ad-hoc subprocess finishes:

- `PrepareCaseSummary` combines every specialist's `summary` into one readable block.
- `All resolved?` evaluates whether every specialist that ran reported `resolved`.
- `yes` calls `NotifyCustomer` (REST to [httpbin.io](https://httpbin.io)) and ends automatically.
- `no` (or nothing ran) creates the user task `Review escalated case`.

All connector calls are governed by Camunda: retried automatically on failure, versioned, fully visible and audited in Operate, and constrained to exactly the tools modeled in each ad-hoc sub-process - no agent can call anything that isn't actually there.

### Focus: orchestration, not conversation

Real customer-support agents often need several back-and-forth messages to gather missing details. Modeling that live chat loop (ask the customer, wait, re-prompt) is a real and separate pattern - Camunda's own [CamundaCon bank-support demo](https://github.com/berndruecker/bank-support-agent-demo) shows it in full, wired to real core-banking, RPA, and vector-DB systems.

This example deliberately leaves that out so the *orchestration* - which agent(s) to call, in what order, how to combine what comes back - stays the whole story. Every scenario is self-contained (all facts a specialist needs are already in the one message), so no specialist ever needs to ask a follow-up question, and nothing about the demo becomes unrealistic - real requests very often do arrive complete.

### Why not A2A?

Camunda 8.9 also lets an agent invoke external agents over the [A2A protocol](https://academy.camunda.com/c8-configure-a2a-connectors), which is the right tool when the specialist truly lives outside Camunda. This example doesn't use it: A2A client connectors are still early-access in 8.9, and there's no stable, free, no-auth public A2A server to depend on for an import-and-run demo (unlike the plain REST/GraphQL services this repo already leans on elsewhere). Delegating to specialist agents that are themselves Camunda processes - via Call Activity - is the native, stable way to get the same "agent calls agent" behavior today. Swapping one Call Activity for a real A2A Client tool once a suitable public agent exists would be a natural follow-up.

## Demo scenarios

The start form's **Scenario** dropdown carries all four sample requests directly - no need to type anything by hand, as long as **Customer request** is left blank (it always overrides the dropdown when filled in).

### Loan question (resolved automatically)

`I'm refinancing my $240,000 mortgage at 6.5% interest over 30 years - what would my new monthly payment be?`

The orchestrator delegates to the Loan Support Agent only, which computes the payment and resolves the case.

### Loan + account question (resolved automatically, calls two agents)

`Please check whether DE89370400440532013000 is a valid IBAN for my transfer, and also tell me what my monthly payment would look like on a $200,000 loan at 6% over 30 years.`

This is the scenario that shows off the orchestrator pattern: it delegates to **both** the Account Support Agent and the Loan Support Agent, then combines both resolutions into one summary. `DE89370400440532013000` is the textbook example IBAN (used throughout ISO 13616 documentation), so it always passes checksum validation.

### Account question (needs human review)

`Can you confirm whether DE89370400440532013001 is a valid account number before I set up a transfer?`

Same IBAN as above with the last digit changed, so its checksum fails validation deterministically. The Account Support Agent reports `needs-human`, and the case routes to the review task.

### Card question (resolved automatically)

`I don't recognize a small charge on my card - the first six digits are 453201, can you tell me which bank issued it?`

The Card Support Agent looks up the BIN and resolves the case regardless of whether a specific issuing bank is found - a successful lookup attempt is enough.

## Local/self-managed path (advanced)

Use this only if you need local Docker-based setup with your own LLM.

1. [Install a local LLM](https://docs.camunda.io/docs/next/guides/getting-started-agentic-orchestration/#set-up-ollama) (or use hosted credentials).
2. Configure environment variables (example for local Ollama):

```env
SECRET_CAMUNDA_PROVIDED_LLM_API_ENDPOINT=http://localhost:11434/v1
SECRET_CAMUNDA_PROVIDED_LLM_API_KEY=null
SECRET_CAMUNDA_PROVIDED_LLM_DEFAULT_MODEL=gpt-oss:20b
```

3. Start [Camunda 8 Run](https://docs.camunda.io/docs/self-managed/quickstart/developer-quickstart/c8run/).
4. Deploy all four files in [models/](models/): `bank-support-orchestrator.bpmn`, `bank-support-loan-agent.bpmn`, `bank-support-account-agent.bpmn`, `bank-support-card-agent.bpmn`, and both forms.
5. Start an instance via Tasklist's form (pick a scenario from the dropdown) or with the same sample variables shown above.

Camunda secrets read `secrets.<NAME>` from same-named environment variables.

## Optional: see live notifications

By default, `NotifyCustomer` posts to `https://httpbin.io/post` (echo response only). This runs after a case resolves automatically.

For a live demo:

1. Create a unique URL at [webhook.site](https://webhook.site/).
2. Replace the `url` input in the `NotifyCustomer` task.
3. Start a new instance and observe incoming requests in webhook.site.

## Notes and disclaimer

This is an illustrative demo only.

- Public services are stand-ins for real enterprise systems (core banking, fraud/compliance platforms).
- No real customer, account, or card data is used - the sample IBAN is the standard textbook example.
- Nothing here should be interpreted as banking or regulatory guidance.

Please be respectful of shared public services. This example is intentionally low-volume and not intended for load testing.
