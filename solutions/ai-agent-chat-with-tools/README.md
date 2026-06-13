# AI Agent Chat With Tools Example

This example demonstrates how to deploy and run an AI-driven chat process in Camunda 8, where an AI agent can answer questions and use external tools (APIs, scripts, etc.) to provide more accurate responses. The process showcases tool integration, user feedback, and human-in-the-loop capabilities.

---

## 🚀 Zero-config LLM on Camunda SaaS

**Running on Camunda SaaS?** This blueprint is already pre-configured to use the **Camunda-provided LLM** — a fully managed model that works out of the box. The required secrets (`CAMUNDA_PROVIDED_LLM_API_ENDPOINT` and `CAMUNDA_PROVIDED_LLM_API_KEY`) are automatically available on Camunda SaaS — no AWS account, no external API keys, no extra setup.

👉 [Learn about the Camunda-provided LLM](https://docs.camunda.io/docs/components/agentic-orchestration/camunda-provided-llm/)

Just deploy the process to your SaaS cluster and run — the AI is ready to go.

---

## Prerequisites

- **Camunda 8.8+** (SaaS or Self-Managed)
- Access to Camunda Connectors (Agentic AI, HTTP, etc.)
- Outbound internet access for connectors (to reach APIs)
- (Optional) Credentials for any external APIs/tools you want to use

---

## Secrets & Configuration

This example is pre-configured to use the **Camunda-provided LLM** via the `CAMUNDA_PROVIDED_LLM_API_ENDPOINT` and `CAMUNDA_PROVIDED_LLM_API_KEY` secrets, which are **automatically available on Camunda SaaS** — no additional secrets needed.

If you want to use a different LLM provider (e.g. AWS Bedrock), update the Agentic AI connector configuration in the process and set up the corresponding credentials:

| Secret Name                  | Purpose                        |
|------------------------------|--------------------------------|
| `AWS_BEDROCK_ACCESS_KEY`     | AWS Bedrock access key         |
| `AWS_BEDROCK_SECRET_KEY`     | AWS Bedrock secret key         |
| ...                          | ...                            |

Configure the connectors in the Web Modeler or via environment variables as needed.

---

## How to Deploy & Run

1. **Import the BPMN Model**
	- Open Camunda Web Modeler.
	- Import `ai-agent-chat-with-tools.bpmn` and all the form files from this folder.

2. **Configure Connectors**
	- Configure any HTTP connectors or other tools you want the agent to use.
    - Feel free to add your own tools by creating new activities in the `AI Agent` ad-hoc sub-process.

3. **Set Secrets**
	- In Camunda Console, add any required secrets (see above).
    - If you use c8run, set the secrets as environment variables and restart `c8run`
    - If you use c8run with Docker, add the secrets in the `connector-secrets.txt` file and restart `c8run`

4. **Deploy the Process**
	- Deploy the process to your Camunda 8 cluster.

5. **Start a New Instance**
	- Use the Web Modeler to start an instance by filling out the form to start an instance.
	- Use tasklist to fill out the form to start a new instance.

6. **Interact**
	- The agent will respond, possibly using tools.

---

## BPMN Process Overview

The process (`ai-agent-chat-with-tools.bpmn`) works as follows:

1. **Start Event**: User submits an initial chat request via a form.
2. **AI Agent Task**: The Agentic AI connector receives the request, context, and available tools. It generates a response and may request tool calls.
3. **Tool Call Gateway**: If the agent wants to use tools, the process enters the `Agent Tools` ad-hoc sub-process.
4. **Agent Tools Sub-Process**: For each tool call requested by the agent, the corresponding task is executed. Tools include:
	- List users (HTTP API)
	- Search recipe (HTTP API)
	- Jokes API (HTTP API)
	- Get list of Tech Stuff (HTTP API)
5. **Loopback**: Tool results are returned to the agent, which may generate further tool calls or a final answer.
6. **User Feedback**: The user is asked if they are satisfied with the answer.
	- If not, the process loops for follow-up.
	- If yes, the process ends.

**Key Features:**
- Dynamic tool invocation by the agent
- Extensible: add your own tools as new tasks in the sub-process

---

## Example Usage

Example inputs which can be entered in the initial form:

- `Tell me a joke`: the agent will use the Jokes API tool to fetch a joke.
- `Find me a recipe for pasta`: the agent will use the Search recipe tool.
- `Which user have the longest name`: the agent will use the List users tool to retrieve user data.
- `Which iPhones are available` will call the tech API for available gadgets and filter for iPhones.

---

## Testing with Camunda Process Test (CPT)

Tests live in `test/`. Two suites: **process tests** (fast, no credentials) and **integration tests** (real connectors + Bedrock).

### Prerequisites

- Java 21+
- Docker running (for process tests)
- AWS Bedrock credentials in `.env` (for integration tests):
  ```
  AWS_BEDROCK_ACCESS_KEY=<your-access-key>
  AWS_BEDROCK_SECRET_KEY=<your-secret-key>
  ```

### Process tests — fast, no credentials needed

Mocks the AI agent job. Tests outer-process routing: happy path and the loop path.

```bash
cd test
mvn test
```

### Integration tests — real connectors + real Bedrock

Runs 5 REST endpoint isolation tests (one per HTTP connector), 5 agent tool-routing tests (real Bedrock), and 2 E2E tests.

```bash
cd test
env $(cat ../.env | grep -v '^#' | xargs) mvn clean test -P integration-test
```

> **Always use `mvn clean`** when switching between process and integration profiles — stale `target/test-classes` from the previous run can cause both test sets to run together.

### What's covered

| Suite | What runs | LLM calls |
|-------|-----------|-----------|
| `mvn test` | Outer-process routing (mocked agent) | None |
| `mvn clean test -P integration-test` | REST connector isolation + agent tool-routing + E2E | Yes (Bedrock) |

---

## EU AI Act Transparency Tagging Guidance (Article 50(2))

Use a clear AI-generated label whenever users see model output.

- The form already demonstrates this in `ai-agent-chat-user-feedback.form`.
- Keep the disclosure close to `responseText` (before or after is fine if it is clearly visible).
- Keep it visible on each response turn, including follow-ups.
- Example text: `AI-generated content: This response was generated by an AI system and may contain mistakes. Please review before relying on it.`

Use the current form implementation as the recommended how-to example.

_Made with ❤️ by Camunda_
