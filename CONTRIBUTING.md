# Contributing to Camunda 8 Tutorials

Thank you for contributing! This repo is the source of truth for the process blueprints published to the [Camunda Marketplace](https://marketplace.camunda.com). Every solution must be correct, deployable, and testable.

---

## Table of Contents

- [What belongs in this repo](#what-belongs-in-this-repo)
- [Creating a shareable, runnable example](#creating-a-shareable-runnable-example)
- [BPMN modeling guidelines](#bpmn-modeling-guidelines)
- [Testing requirements](#testing-requirements)
- [BPMN linting](#bpmn-linting)
- [PR checklist](#pr-checklist)

---

## What belongs in this repo

Each solution in `solutions/<name>/` must contain:

| File/directory | Required | Notes |
|----------------|----------|-------|
| `*.bpmn` | Yes | Deployable Camunda 8 BPMN process |
| `*.dmn` (if applicable) | Yes | DMN decision tables referenced by the process |
| `*.form` (if applicable) | Yes | Form files referenced by user task `zeebe:formDefinition` |
| `README.md` | Yes | What the process does, how to deploy and run it |
| `test/` | Yes | CPT 8.9 process test suite (see [Testing requirements](#testing-requirements)) |

Quick-start tutorials in `quick-start/<name>/` follow the same structure.

**Examples** in `examples/<name>/` follow the same structure, plus one more file —
see [Creating a shareable, runnable example](#creating-a-shareable-runnable-example):

| File/directory | Required | Notes |
|----------------|----------|-------|
| `camunda-example.json` | Yes | The manifest that makes this example linkable and embeddable anywhere — see below |
| `docs/` | Recommended | Preview image(s) referenced by the manifest's `thumbnail` field |

---

## Creating a shareable, runnable example

We love show don't tell. So whenever we are describing patterns or making a point, we try to also provide a runnable example.
To make this easy to share, but also easy to try out for users we strive for a consistent format for those examples.

We have infrasturcture to read metadata for an example from a file and render info cards for it on the camunda.com website, blogposts, but also create embeddable cards for third party sides, personal blog posts, etc...

![A runnable example card, with a process preview, a description, a "Run in SaaS" button and a "Read the docs" link](docs/images/runnable-example-card.png)

The whole thing hinges on a single manifest, `camunda-example.json`, committed next to your BPMN/DMN/Form files. Nothing else needs to change or be built —
this file *is* the mechanism. For example, [`examples/task-agent/camunda-example.json`](examples/task-agent/camunda-example.json):

```json
{
  "$schema": "https://camunda.com/schemas/runnable-example/v1.json",
  "title": "Seed export compliance agent",
  "subtitle": "A runnable Task Agent",
  "description": "One agent subprocess, three real tool calls — a SQL lookup, a GraphQL API, and a REST calculation. It reads free-form shipment notes, then clears compliant shipments automatically or hands the rest to a human for review.",
  "creator": "Camunda",
  "camundaVersion": "8.8+",
  "thumbnail": "docs/seed-export-compliance-agent.png",
  "thumbnailAlt": "BPMN model: a Compliance Check Agent subprocess with SQL, GraphQL, REST and record-decision tools, routing to an export-team notification or a human review task.",
  "resources": [
    "models/seed-export-compliance-agent.bpmn",
    "models/seed-export-shipment-ready.form",
    "models/seed-export-compliance-review.form"
  ],
  "importTitle": "Seed Export Compliance Agent",
  "documentation": "https://github.com/camunda/camunda-8-tutorials/tree/main/examples/task-agent",
  "support": "https://forum.camunda.io/"
}
```

| Field | Required | Notes |
|---|---|---|
| `title` | ✅ | Headline shown on every card |
| `description` | ✅ | One or two plain-language sentences — this is marketing copy, not a spec |
| `resources` | ✅ | Repo-relative paths to every `.bpmn`/`.dmn`/`.form` file the example needs — all get bundled into one Web Modeler import |
| `thumbnail` | recommended | Repo-relative path to a preview image of the process model (a screenshot of the diagram is enough) |
| `thumbnailAlt` | recommended | Describe the diagram's shape (agent box, tools, gateway, outcomes) — read by screen readers and used as SEO alt text |
| `importTitle` | optional | Title shown inside Web Modeler after import; defaults to `title` |
| `documentation` | optional | Defaults to this folder's GitHub URL — set explicitly only if docs live elsewhere |
| `subtitle`, `creator`, `camundaVersion`, `support` | optional | Not yet rendered everywhere; carried along for future use and Marketplace submission |

Field names deliberately mirror the
[Camunda Marketplace blueprint submission](https://marketplace.camunda.com/en-US/pages/submitBlueprint)
fields, so a well-documented example here is most of the way to a Marketplace
listing already, if you choose to submit it. You can add fields beyond this
list if a future consumer needs them (see `sampleVariables` in
`examples/task-agent/camunda-example.json` for an example — used to seed a
one-click test run); unrecognized fields are ignored, so this is safe to do
incrementally.

### How to use it

1. **Build the example.** Same rules as any solution or quick-start: correct,
   deployable, tested (see [What belongs in this repo](#what-belongs-in-this-repo)
   and [Testing requirements](#testing-requirements)).
2. **Add `camunda-example.json`** next to the BPMN/DMN/Form files, as above.
3. **Hand out the folder URL** — not the manifest file, the *folder*, e.g.:
   ```
   https://github.com/camunda/camunda-8-tutorials/tree/main/examples/task-agent
   ```
   That URL is the only thing anyone downstream needs — the website team for a
   camunda.com card, a blog author pasting it into a Sanity block, you dropping
   it into an `<iframe>` on a partner site, or just a plain link in Slack or a
   slide (it also works as-is: opening the folder on GitHub is a perfectly fine
   fallback for anyone who doesn't use the card at all).

For exactly how camunda.com turns that URL into a card, a blog embed, or an
external `<iframe>`, see
[`camunda.com-website/.claude/docs/runnable-examples.md`](https://github.com/camunda/camunda.com-website/blob/main/.claude/docs/runnable-examples.md)
in the website repo — that's the spec for how the manifest gets consumed.
Consider also adding your example to this repo's own [README](README.md) under
"Examples" so people browsing the repo can find it.

## BPMN modeling guidelines

These guidelines follow the [Camunda best practices](https://docs.camunda.io/docs/components/best-practices/best-practices-overview/). Apply them when creating or updating BPMN files.

### Element naming

> **Goal**: A business stakeholder unfamiliar with the system should be able to read the diagram and understand what happens.

| Element | Convention | Example |
|---------|-----------|---------|
| **Activities** (tasks, sub-processes) | Object + verb in infinitive | `Approve loan application`, `Send rejection email` |
| **Start/end events** | Noun phrase describing business state | `Application received`, `Loan approved`, `Application rejected` |
| **Intermediate events** | Noun phrase describing what occurred | `Payment confirmed`, `Timeout exceeded` |
| **Gateways** | Question that the flow answers | `Application complete?`, `Loan approved?` |
| **Sequence flows from gateways** | Short answer to the gateway question | `Yes` / `No`, `Approved` / `Rejected` / `Needs review` |

**Rules:**
- Use sentence case (first word capitalised, rest lowercase).
- Avoid vague verbs: `Handle`, `Process`, `Manage`, `Do`. Use specific ones.
- Avoid technical terms, class names, or system identifiers as visible labels.
- Keep names short; use a text annotation to explain complex logic.

### Element IDs

Follow the [Camunda ID naming conventions](https://docs.camunda.io/docs/next/components/best-practices/modeling/naming-technically-relevant-ids/). IDs must be set in the Camunda Modeler properties panel — never edited directly in raw XML.

| Element | ID pattern | Example |
|---------|-----------|---------|
| **Process** | `[ProcessName]Process` (PascalCase) | `LoanApprovalProcess` |
| **BPMN file** | Matches process ID | `LoanApprovalProcess.bpmn` |
| **Start event** | `StartEvent_[Trigger]` | `StartEvent_ApplicationReceived` |
| **End event** | `EndEvent_[Outcome]` | `EndEvent_LoanApproved`, `EndEvent_LoanRejected` |
| **Task / sub-process** | `Task_[Action]` | `Task_ReviewApplication`, `Task_SendRejectionEmail` |
| **Gateway** | `Gateway_[Condition]` | `Gateway_ApplicationApproved` |
| **Sequence flow** (from gateway) | `SequenceFlow_[Outcome]` | `SequenceFlow_Approved`, `SequenceFlow_Rejected` |
| **Boundary event** | `BoundaryEvent_[EventType]` | `BoundaryEvent_ApplicationTimedOut` |
| **Message** | `Message_[Name]` | `Message_ApplicationReceived` |
| **Error** | `Error_[Type]` | `Error_InvalidApplication` |

**IDs should reflect their element's name** — a reviewer reading the XML should be able to understand what each element does from its ID alone.

**Existing blueprints** that were created in Web Modeler may have auto-generated IDs (e.g., `Process_l1jbzht`, `Activity_03z512o`). Rename these to follow the naming convention above — the process ID should match the BPMN filename, and all element IDs should be descriptive. Update any `.test.json` references at the same time.

### Modeling structure

Follow [creating readable process models](https://docs.camunda.io/docs/next/components/best-practices/modeling/creating-readable-process-models/):

- **Left to right**: the happy path flows left-to-right. Sequence flows never go to the left of their source symbol.
- **Happy path in the centre**: exception and error paths branch above or below the main flow.
- **Explicit gateways**: always use gateway symbols for branching and merging — never use conditional sequence flows directly on tasks.
- **Separate split and join**: use distinct gateway symbols for splitting and joining — do not reuse the same gateway for both.
- **XOR marker explicit**: exclusive gateways must show the X marker, not be left unmarked.
- **Matching gateway pairs**: every split gateway has a corresponding join gateway that closes the branch, forming a visual block.
- **Explicit start and end events**: every process has exactly one start event and one distinctly named end event per business outcome.
- **No overlapping flows**: sequence flows must not cross or overlap; rearrange elements to minimise crossings.
- **No multi-page flows**: do not draw a sequence flow that spans a page break — use link events instead.
- **Minimise lanes**: lanes conflict with symmetric layout and increase maintenance cost. Reserve them for models where swimlane ownership is the primary message; prefer collaboration pools instead.

### Exception and error handling

Follow [dealing with problems and exceptions](https://docs.camunda.io/docs/components/best-practices/development/dealing-with-problems-and-exceptions/):

- Model **boundary error events** on service tasks that can fail with known business errors.
- Use **event sub-processes** for exceptions that can occur at any point in the process.
- Use **timer boundary events** for timeouts (SLA enforcement, waiting periods).
- Do not model retry loops in the BPMN — use the `retries` attribute on `zeebe:taskDefinition` instead.

### Documentation within the diagram

- Add a **text annotation** on the process start event (or pool) describing: what triggers the process, who is involved, and what the happy-path outcome is.
- Add text annotations to explain non-obvious gateway conditions or variable usage.

---

## Testing requirements

Every solution must have a passing **Camunda Process Test (CPT 8.9)** suite in `<solution>/test/`.

### Structure

```
<solution>/test/
  pom.xml
  src/test/java/io/camunda/tests/
    ProcessTest.java        # or JsonProcessTest.java for Agentic AI processes
    TestApplication.java
  src/test/resources/
    application.yml
    scenarios/              # or test-cases/ for JSON-driven tests
      *.test.json
```

### What tests must cover

At minimum, tests must cover:

1. **Happy path** — the process reaches a successful end event when given valid inputs.
2. **Key decision branches** — at least one test per significant gateway outcome (e.g., approved vs. rejected).
3. **Error paths** — if the process has error boundary events or event sub-processes, test at least one error scenario.

### Integration test notes

Every test file must include `_integrationTestNotes` that describes what real integration tests should verify when a live environment is available. Keep these updated as connectors and external systems change.

### Running tests locally

Tests require Docker (embedded Zeebe runs via Testcontainers):

```bash
cd solutions/<name>/test
mvn test
```

All tests must pass before a PR can be merged.

### CPT patterns reference

See [`docs/testing-guide.md`](https://github.com/camunda/camunda-ai-dev-kit/blob/main/docs/testing-guide.md) in the `camunda-ai-dev-kit` repo for documented CPT 8.9 patterns and common failure causes, including:

- Agentic AI (Ad-Hoc Sub-Process) testing with `COMPLETE_JOB_AD_HOC_SUB_PROCESS`
- Form file deployment requirements
- Message and connector start event workarounds
- Webhook message UUID names
- DMN explicit deployment requirements

---

## BPMN linting

All BPMN and DMN files must pass `bpmnlint` with the Camunda compatibility rules:

```bash
# Install (one-time)
npm install -g bpmnlint bpmnlint-plugin-camunda-compat

# Lint a file
bpmnlint solutions/<name>/<process>.bpmn
```

The repo's `.bpmnlintrc` config (in the project root) specifies the active rule set. CI runs this automatically on changed BPMN/DMN files.

**Common lint errors and how to fix them:**

| Error | Fix |
|-------|-----|
| `Element must have a name` | Add a `name` attribute to the element in the Modeler |
| `Service task must have a job type` | Set a `zeebe:taskDefinition type` on the task |
| `Sequence flow conditions required` | Label all outgoing flows from exclusive gateways with a condition |
| `No start event` | Ensure every process has exactly one valid start event |

If a rule is a false positive for the tutorial context (e.g., a connector type that bpmnlint doesn't recognise), add a targeted override to `.bpmnlintrc` with a comment explaining why.

---

## PR checklist

### Automatic checks (run by CI and AI on every PR)

These run automatically when a PR is opened or updated. Fix any failures before requesting review.

**CI (blocks merge):**
- `mvn test` passes for all affected solution test suites
- `bpmnlint` reports no errors on changed `.bpmn` / `.dmn` files
- All changed BPMN / DMN files are well-formed XML
- No `target/` build artifacts or `.env` files included

**AI review (posted as a PR comment):**

*Element naming* ([reference](https://docs.camunda.io/docs/next/components/best-practices/modeling/naming-bpmn-elements/))
- Task and sub-process names use object + infinitive verb ("Send rejection email", not "Handle email")
- Event names describe a business state ("Loan approved", not "Loan processed")
- Gateway names are posed as questions ("Application complete?")
- Sequence flows from gateways are labelled as answers to the gateway question
- No technical terms, class names, or system identifiers used as visible labels
- Sentence case throughout (first word capitalised, rest lowercase)
- No unexplained abbreviations

*Element IDs* ([reference](https://docs.camunda.io/docs/next/components/best-practices/modeling/naming-technically-relevant-ids/))
- New process IDs follow `[ProcessName]Process` (PascalCase); BPMN filename matches
- New task IDs use `Task_[Action]` prefix
- New gateway IDs use `Gateway_[Condition]` prefix
- New start/end event IDs use `StartEvent_` / `EndEvent_` prefix
- New boundary event IDs use `BoundaryEvent_[EventType]` prefix
- New message IDs use `Message_[Name]` prefix; error IDs use `Error_[Type]` prefix
- Each ID reflects its element's visible name (readable in raw XML without the diagram)
- No existing published IDs have been renamed without updating all test references

*Readable structure* ([reference](https://docs.camunda.io/docs/next/components/best-practices/modeling/creating-readable-process-models/))
- Happy path flows left-to-right with no backward-pointing sequence flows
- Exception paths branch off above or below the main flow
- Start and end events are explicit and each end event has a distinct business name
- Gateways are used for all branching (no conditional flows directly on tasks)
- Split and join gateways are separate symbols; XOR gateways show the X marker
- No overlapping or crossing sequence flows where avoidable
- Retry logic is not modelled in the diagram (handled via `retries` on `zeebe:taskDefinition`)
- Symbol sizes use the Modeler default; colour used sparingly and consistently

*Exception handling* ([reference](https://docs.camunda.io/docs/next/components/best-practices/modeling/modeling-beyond-the-happy-path/))
- Business-level errors are modelled as boundary error events or event sub-processes
- Timer boundary events are used for SLA / timeout paths (not retry loops)
- Event-based gateways are used when the process passively waits for external events
- Each distinct business outcome (including error outcomes) has a named end event

*Completeness*
- Any DMN or form files referenced by the BPMN are present in the solution directory
- A text annotation on the start event or pool describes the process purpose

*Tests*
- Key gateway branches each have a corresponding test case
- Test scenarios use realistic variable values (not empty strings or placeholder IDs)
- `_integrationTestNotes` is specific — names the connectors, APIs, and variables to verify

*README*
- `README.md` covers what the process does, how to deploy it, and what inputs it expects

### Human checks (reviewer judgment only)

- [ ] The process correctly models the intended business domain — the flow makes sense for the use case
- [ ] The level of detail is appropriate for a tutorial audience (not over-engineered, not underspecified)
