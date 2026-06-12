# Beeceptor fixtures — hand-off for the mock owner

The process integration tests (`ClaimsProcessingAgentIT`) drive the real assessment agent, whose
routing follows the data the tool connectors return from `https://claim-demo.free.beeceptor.com`.
To make each business outcome deterministic, the mock must return fixed JSON keyed by the claim and
customer id used in each test. The fraud outcome already works (the existing `CLM-2025-0042` data);
the approve and manual-review outcomes are `@Disabled` in the test suite until the fixtures below
exist.

Endpoints (path-keyed, so per-id responses are stable):
- `GET /claims/{claimId}` — policy lookup
- `GET /customers/{customerId}` — customer profile
- `POST /claims/{claimId}/estimate` — damage estimate

## Fixtures to add

### PIR-2 — clean claim, must drive APPROVE
Ids: claim `CLM-IT-CLEAN-001`, customer `CUST-IT-CLEAN`.

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

### PIR-3 — borderline claim, must drive MANUAL_REVIEW
Ids: claim `CLM-IT-BORDER-001`, customer `CUST-IT-BORDER`.

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

### PIR-1 — fraud claim (already present, for reference)
Ids: claim `CLM-2025-0042`, customer `CUST-4521`. Returns high fraud risk, prior fraud history,
multiple recent claims, and an estimate far above vehicle value. No change needed.

## After provisioning
Remove the `@Disabled` annotations on `cleanClaimIsApproved` (PIR-2) and
`borderlineClaimGoesToManualReview` (PIR-3) in
`test/src/test/java/io/camunda/tests/ClaimsProcessingAgentIT.java`, then re-run
`mvn test -P integration-test`. Those two tests then prove their full APPROVE / MANUAL_REVIEW paths
and raise process-integration coverage.
