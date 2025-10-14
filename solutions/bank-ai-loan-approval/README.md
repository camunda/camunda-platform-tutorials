# Camunda AI Email Support Blueprint (Alpha)

> **Important ⚠️**  This blueprint targets **Camunda 8.8.0-alpha6** clusters (SaaS or Self-Managed).  The **AI Agent Connector**, **Vector Database Connector** used here are **alpha** components; breaking changes can occur between alpha releases.

A ready-to-import solution that demonstrates an AI-driven email conversation loop with:

* Email inbound & outbound handling via the **generic Email Connector** (SMTP/IMAP provider agnostic)
* Short-term conversation memory
* Long-term memory through an OpenSearch vector index
* Knowledge-base grounding for context-aware replies
* Automatic—or human-assisted—response generation using Camunda **AI Agents**

---

## 1 · one-click import  🡒  **Web Modeler link**

[Download BPMN Files](https://modeler.cloud.camunda.io/import/processes?source=https://raw.githubusercontent.com/bastiankoerber/Camunda_Agent_Blueprint/refs/heads/main/Agent%20Blueprint%20(Long%20Term%20Memory).bpmn,https://raw.githubusercontent.com/bastiankoerber/Camunda_Agent_Blueprint/refs/heads/main/Escalate%20to%20human%20form.form,https://raw.githubusercontent.com/bastiankoerber/Camunda_Agent_Blueprint/refs/heads/main/Review%20case%20resolution.form)

This link imports **all required artifacts** in two steps:

| Artifact                                                              | Source          |
|-----------------------------------------------------------------------|-----------------|
| **BPMN** – `Agent%20Blueprint%20(Long%20Term%20Memory).bpmn`          | this repository |
| **Forms** – `form-escalate-human.form`, `form-review-resolution.form` | this repository |

---

## 2 · Prerequisites

| Requirement                             | Notes                                                                                              |
|-----------------------------------------|----------------------------------------------------------------------------------------------------|
| **Camunda 8.8.0-alpha6** cluster        | Cloud SaaS or Self-Managed;                                                                        |
| Email account (SMTP/IMAP) & credentials | For Gmail use an App Password; for others use provider-specific credentials.                       |
| AWS IAM user                            | Permissions: `bedrock:InvokeModel` (Claude 3 Sonnet/Haiku) and `aoss:*` for your OpenSearch index. |
| Outbound internet access                | Connectors must reach your email server, Bedrock, and OpenSearch endpoints.                        |

---

## 3 · Secrets to create in the cluster

| Secret key                            | Purpose                                                      |
|---------------------------------------|--------------------------------------------------------------|
| `CAMUNDA_SAMPLE_AGENT_EMAIL_PASSWORD` | **Email account password** (App Password or SMTP token)      |
| `CAMUNDA_SAMPLE_AGENT_EMAIL_USERNAME` | **Email account username** (e.g. `your-address@example.com`) |
| `CAMUNDAAGENT_AWS_ACCESS_KEY`         | AWS **Access Key ID**                                        |
| `CAMUNDAAGENT_AWS_SECRET_KEY`         | AWS **Secret Access Key**                                    |

---

## 4 · Configuration steps (after import)

** YOU CAN FIND THE AWS CONFIGURATION IN THE FIRST SCRIPT TASK **

1. **Email connectors** (Inbound & Send):
   * **Username** → `your-address@example.com`
   * **IMAP/SMTP host & port** → according to your provider (Gmail, Outlook, custom, …)
2. **Vector Database connectors** (Retrieve & Write):
   * **Region** → your AWS region (e.g. `eu-central-1`)
   * **Endpoint** → `https://<your-opensearch-domain>`
4. **Agent connector**:
   * **Model ID** → default `anthropic.claude-3.7-sonnet-20240229-v1:0`, change as needed.

---

## 5 · Repository layout

```
blueprint/
├── email-support.bpmn
├── form-escalate-human.form
└── form-review-resolution.form
README.md
```

---


Made with ❤️ by Camunda Product & AI teams  ·  _Alpha preview – feedback welcome!_
