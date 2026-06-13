# AI Agent Chat With MCP Example

This example demonstrates how to deploy and run an AI-driven chat process integrating tools exposed by [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) servers by
integrating with the [MCP Client](https://docs.camunda.io/docs/components/early-access/alpha/mcp-client/) connectors.

ℹ️ As this example relies on MCP clients being configured as part of the connector runtime, it needs additional configuration only available in self-managed/[hybrid](https://docs.camunda.io/docs/components/connectors/use-connectors-in-hybrid-mode/) environments.

⚠️ This example works on **Camunda 8.8** only. For a version compatible with Camunda 8.9 and later, see the [updated version](https://github.com/camunda/connectors/tree/main/connectors/agentic-ai/examples/ai-agent/ad-hoc-sub-process/ai-agent-chat-mcp) of this example managed as part of the connectors codebase.

---

## 🚀 Camunda-provided LLM — zero config on SaaS

The **MCP client configuration** in this blueprint requires a self-managed or hybrid setup, but the AI model itself doesn't have to be self-hosted. This blueprint is pre-configured to use the **Camunda-provided LLM** via the `openaiCompatible` provider. When running in [hybrid mode](https://docs.camunda.io/docs/components/connectors/use-connectors-in-hybrid-mode/) alongside Camunda SaaS, the required secrets (`CAMUNDA_PROVIDED_LLM_API_ENDPOINT` and `CAMUNDA_PROVIDED_LLM_API_KEY`) are automatically available — no external LLM credentials needed.

👉 [Learn about the Camunda-provided LLM](https://docs.camunda.io/docs/components/agentic-orchestration/camunda-provided-llm/)

---

## Prerequisites

- **Camunda 8.8** (Self-Managed)
- Access to Camunda Connectors (Agentic AI, MCP Client)
- Outbound internet access for connectors (to reach APIs)

---

## Secrets

This example is pre-configured to use the **Camunda-provided LLM** via the `CAMUNDA_PROVIDED_LLM_API_ENDPOINT` and `CAMUNDA_PROVIDED_LLM_API_KEY` secrets. When running in hybrid mode with a Camunda SaaS cluster, these secrets are automatically available.

If you want to use a different LLM provider (e.g. AWS Bedrock), update the Agentic AI connector configuration and create the corresponding secrets:

| Secret Name              | Purpose                |
|--------------------------|------------------------|
| `AWS_BEDROCK_ACCESS_KEY` | AWS Bedrock access key |
| `AWS_BEDROCK_SECRET_KEY` | AWS Bedrock secret key |
| ...                      | ...                    |

Configure the connectors in the Web Modeler or via environment variables as needed.

## Runtime Configuration

The [MCP Client connector](https://docs.camunda.io/docs/components/early-access/alpha/mcp-client/mcp-client-connector/) used for the OpenMemory and filesystem tools requires configuration of MCP clients in the connector runtime. See the [documentation](https://docs.camunda.io/docs/components/early-access/alpha/mcp-client/mcp-client-connector/)
on how to configure the runtime for your environment with the following clients:

```yaml
camunda:
  connector:
    agenticai:
      mcp:
        client:
          enabled: true # <-- disabled by default
          clients:
            # STDIO filesystem server started via NPX (make sure you have a Node.js environment available)
            # replace path to files to the directory you want the model to have access to
            # you can also add multiple paths, see https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem
            filesystem:
              stdio:
                command: npx
                args:
                  - '-y'
                  - '@modelcontextprotocol/server-filesystem'
                  - '<path-to-files>'

            # STDIO servers can be started in any runtime/language, e.g. as docker container
            time:
              stdio:
                command: docker
                args:
                  - 'run'
                  - '-i'
                  - '--rm'
                  - 'mcp/time'

            # Remote HTTP/SSE MCP server
            # start the OpenMemory MCP server first as documented on https://docs.mem0.ai/openmemory/overview#openmemory-easy-setup
            openmemory:
              sse:
                # replace with the URL returned by the OpenMemory MCP link UI
                url: http://localhost:8765/mcp/openmemory/sse/<your-client-id>
```

---

## How to Deploy & Run

1. **Import the BPMN Model**
    - Open Camunda Web Modeler.
    - Import `ai-agent-chat-with-mcp.bpmn` and all the form files from this folder.

2. **Set Secrets**
    - In Camunda Console, add any required secrets (see above).
    - If you use c8run, set the secrets as environment variables and restart `c8run`
    - If you use c8run with Docker, add the secrets in the `connector-secrets.txt` file and restart `c8run`

3. **Configure the Connectors Runtime**
    - Add the MCP client configuration (see above).

4. **Deploy the Process**
    - Deploy the process to your Camunda 8 cluster.

5. **Start a New Instance**
    - Use the Web Modeler to start an instance by filling out the form to start an instance.
    - Use tasklist to fill out the form to start a new instance.
    - From Desktop Modeler, you can start the instance by providing an `inputText` variable containing a request. For example:
      ```json
      { "inputText": "Compare the NYC and Berlin timezones and write the results to a markdown file" }
      ```

6. **Interact**
    - The agent will discover tools provided through the MCP servers and use them to fulfill user requests.

---

## Key Features

- Integration of [MCP Client](https://docs.camunda.io/docs/components/early-access/alpha/mcp-client/mcp-client-connector/) and [MCP Remote Client](https://docs.camunda.io/docs/components/early-access/alpha/mcp-client/mcp-remote-client-connector/) connectors
- Human-in-the-loop flow for filesystem operations

---

## Example Usage

Example inputs which can be entered in the initial form:

- `Compare the NYC and Berlin timezones and write the results to a markdown file`
- With the OpenMemory integration activated, do some interactions with the agent and ask it to memorize results. In a new conversation, ask it to recall the memorized results. Core memories should be visible in the OpenMemory dashboard.

---

## EU AI Act Transparency Tagging Guidance (Article 50(2))

Use a clear AI-generated label for user-visible responses.

- The form already includes this pattern in `ai-agent-chat-user-feedback.form`.
- Keep the disclosure close to `responseText` (before or after is fine if it is clearly visible).
- Reuse the same approach for each response cycle, including MCP-assisted responses.
- Example text: `AI-generated content: This response was generated by an AI system and may contain mistakes. Please review before relying on it.`

Use the existing form implementation as the practical how-to for this blueprint.

_Made with ❤️ by Camunda_
