# Beeceptor fixtures — reference and CIR verification

The process integration tests drive the real assessment agent, whose routing follows the data
the tool connectors return from `https://claim-demo.free.beeceptor.com`. Three fixture sets cover
the three business outcomes. Each set is verified by a dedicated CIR test group that asserts on
exact field values — not just element completion — so a misconfigured fixture (e.g. returning
schema templates instead of real JSON) causes a clear CIR failure rather than a silent PIR failure.

Endpoints (path-keyed, so per-id responses are stable):
- `GET /claims/{claimId}` — policy lookup
- `GET /customers/{customerId}` — customer profile
- `POST /claims/{claimId}/estimate` — damage estimate

## Fixture sets

### Fraud fixture — drives ESCALATE (PIR-1)
Ids: claim `CLM-2025-0042`, customer `CUST-4521`.

Verified by **CIR-1, CIR-2, CIR-3** — run with `mvn test -P integration-test`.

`GET /claims/CLM-2025-0042`
```json
{ "policyNumber": "POL-FRAUD", "status": "active", "fraudRiskScore": "high", "fraudFlags": 3,
  "collisionCoverageAddedDate": "2025-10-19" }
```
`GET /customers/CUST-4521`
```json
{ "riskRating": "high", "fraudHistory": true, "priorFraudInvestigations": 1,
  "openInvestigations": 1, "claimsThisYear": 3 }
```
`POST /claims/CLM-2025-0042/estimate`
```json
{ "estimatedAmount": 47800, "repairCategory": "total-loss", "estimateConfidence": "low",
  "anomalyFlags": ["INFLATED_ESTIMATE", "EXCEEDS_MARKET_VALUE"] }
```

CIR assertions: `policyStatus="active"`, `fraudRiskScore="high"`, `riskRating="high"`,
`fraudHistory=true`, `damageEstimate > 30000`.

### Clean fixture — drives APPROVE (PIR-2)
Ids: claim `CLM-IT-CLEAN-001`, customer `CUST-IT-CLEAN`.

Verified by **CIR-6, CIR-7, CIR-8** — run with `mvn test -P integration-test`.

`GET /claims/CLM-IT-CLEAN-001`
```json
{ "policyNumber": "POL-CLEAN", "status": "active", "coverageLimit": 50000, "deductible": 500,
  "fraudRiskScore": "low", "fraudFlags": 0 }
```
`GET /customers/CUST-IT-CLEAN`
```json
{ "tier": "standard", "accountStatus": "active", "riskRating": "low", "fraudHistory": false,
  "priorFraudInvestigations": 0, "openInvestigations": 0, "claimsThisYear": 0 }
```
`POST /claims/CLM-IT-CLEAN-001/estimate`
```json
{ "estimatedAmount": 950, "repairCategory": "minor", "estimateConfidence": "high", "anomalyFlags": [] }
```

CIR assertions: `policyStatus="active"`, `fraudRiskScore="low"`, `riskRating="low"`,
`fraudHistory=false`, `damageEstimate=950`, `damageCategory="minor"`.

### Border fixture — drives MANUAL_REVIEW (PIR-3)
Ids: claim `CLM-IT-BORDER-001`, customer `CUST-IT-BORDER`.

Verified by **CIR-9, CIR-10, CIR-11** — run with `mvn test -P integration-test`.

`GET /claims/CLM-IT-BORDER-001`
```json
{ "policyNumber": "POL-BORDER", "status": "active", "coverageLimit": 50000, "deductible": 500,
  "fraudRiskScore": "medium", "fraudFlags": 1 }
```
`GET /customers/CUST-IT-BORDER`
```json
{ "tier": "standard", "accountStatus": "active", "riskRating": "medium", "fraudHistory": false,
  "priorFraudInvestigations": 0, "openInvestigations": 0, "claimsThisYear": 1 }
```
`POST /claims/CLM-IT-BORDER-001/estimate`
```json
{ "estimatedAmount": 18000, "repairCategory": "moderate", "estimateConfidence": "medium", "anomalyFlags": [] }
```

CIR assertions: `policyStatus="active"`, `fraudRiskScore="medium"`, `riskRating="medium"`,
`fraudHistory=false`, `damageEstimate=18000`, `damageCategory="moderate"`.

## Current fixture status

| Fixture set              | Endpoints live | Returns real JSON | CIR gate          | PIR test        |
|--------------------------|----------------|-------------------|-------------------|-----------------|
| Fraud (CLM-2025-0042)    | Yes            | Yes               | CIR-1/2/3 pass    | PIR-1 enabled   |
| Clean (CLM-IT-CLEAN-001) | Yes            | Yes               | CIR-6/7/8 pass    | PIR-2 @Disabled |
| Border (CLM-IT-BORDER-001)| Yes           | Yes               | CIR-9/10/11 pass  | PIR-3 @Disabled |

All three fixture sets return concrete JSON values. CIR-1 through CIR-11 pass. PIR-2 and PIR-3
remain `@Disabled` pending verification that the live agent routes each clean/border claim
correctly (APPROVE and MANUAL_REVIEW, respectively). Re-enable by removing the `@Disabled`
annotation from each test in `ClaimsProcessingAgentIT.java` and running with `-P integration-test`.

Note on fraud fixture shape: `GET /customers/CUST-4521` nests `fraudHistory` under
`claimHistory.fraudHistory` rather than at the top level. The `extractFraudHistory()` helper
in `ClaimsExternalSystemsIT` handles both layouts.
