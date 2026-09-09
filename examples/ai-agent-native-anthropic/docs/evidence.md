# Preparation evidence

## Released baseline

- Camunda and Camunda Process Test: `8.10.0-alpha5`
- AI Agent element template: `connectors/agentic-ai/connector-agentic-ai/element-templates/agenticai-ai-agent-subprocess.v2.json` from the `8.10.0-alpha5` tag
- Template ID/version: `io.camunda.connectors.agenticai.ai-agent-subprocess.v2` / `1`
- Agent job type: `io.camunda.agenticai:aiagent:subprocess:2`

The authoritative preparation pin is `${camunda.version}` in `test/pom.xml`. The public merge gate requires replacing it with Camunda 8.10 GA and repeating all evidence.

## Evidence status

| Capability | Status | Source |
|---|---|---|
| v2 template shape and fields | Verified on released alpha5 | Pinned template and BPMN lint/deploy |
| BPMN orchestration and failure routes | Deterministically verified | JSON CPT |
| Anthropic wire contract | Deterministically verified | Managed Connector Runtime + WireMock |
| Combined live Anthropic capability | Blocked/provisional | `ANTHROPIC_API_KEY` was absent in the implementation environment |
| Live cache metrics | Not observed | No credentialed live run was performed |
| Recording | Blocked | The approved stop condition requires the combined live capability probe first |

No live result is inferred from deterministic tests, and no provider result is fabricated.
