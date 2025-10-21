# Camunda AI Email Support Blueprint (Long Term Memory)

A ready-to-import solution that demonstrates an AI-driven email conversation loop with:

* Email inbound & outbound handling via the **generic Email Connector** (SMTP/IMAP provider agnostic)
* Short-term conversation memory
* Long-term memory through an OpenSearch vector index
* Knowledge-base grounding for context-aware replies
* Automatic—or human-assisted—response generation using Camunda **AI Agents**

---

## 1 · one-click import  🡒  **Web Modeler link**

Visit the [Camunda Marketplace](https://marketplace.camunda.com/en-US/apps/522492/ai-email-support-agent) and click the SaaS button.

This imports **all required artifacts**:

| Artifact                                                            | Source          |
|---------------------------------------------------------------------|-----------------|
| **BPMN**  – `email-support.bpmn`                                    | this repository |
| **Forms** – `escalate-to-human.form`, `review-case-resolution.form` | this repository |

---

## 2 · Prerequisites

| Requirement                             | Notes                                                                                              |
|-----------------------------------------|----------------------------------------------------------------------------------------------------|
| **Camunda 8.8.0** cluster               | Cloud SaaS or Self-Managed;                                                                        |
| Email account (SMTP/IMAP) & credentials | For Gmail use an App Password; for others use provider-specific credentials.                       |
| AWS IAM user                            | Permissions: `bedrock:InvokeModel` (Claude 3 Sonnet/Haiku) and `aoss:*` for your OpenSearch index. |
| Outbound internet access                | Connectors must reach your email server, Bedrock, and OpenSearch endpoints.                        |

### Tips and tricks for using gmail
- To enable app passwords, you need to enable 2-Step Verification on your Google account first
- Use an App Password for better security: [Google App Passwords](https://myaccount.google.com/apppasswords)
- Remove the spaces from the app password to have a 16 character password and use that for `CAMUNDA_SAMPLE_AGENT_EMAIL_PASSWORD`

---

## 3 · Secrets to create in the cluster

| Secret key                                | Purpose                                                                            |
|-------------------------------------------|------------------------------------------------------------------------------------|
| `CAMUNDA_SAMPLE_AGENT_EMAIL_PASSWORD`     | **Email account password** (App Password or SMTP token)                            |
| `CAMUNDA_SAMPLE_AGENT_EMAIL_USERNAME.   ` | **Email account username** (e.g. `your-address@example.com`)                       |
| `CAMUNDAAGENT_AWS_ACCESS_KEY`             | AWS **Access Key ID**                                                              |
| `CAMUNDAAGENT_AWS_SECRET_KEY`             | AWS **Secret Access Key**                                                          |
| `CAMUNDAAGENT_AWS_LONGTERM_MEMORY_SERVER` | AWS **OpenSearch endpoint** (e.g. `https://search-domain.region.es.amazonaws.com`) |

---

## 4 · Repository layout

```
blueprint/
├── email-support.bpmn
├── escalate-to-human.form
└── review-case-resolution.form
README.md
```

---

Made with ❤️ by Camunda Product & AI teams
