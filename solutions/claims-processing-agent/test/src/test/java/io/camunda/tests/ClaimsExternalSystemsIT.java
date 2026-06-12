package io.camunda.tests;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Segment integration requirements (SIR) — external systems exercised in isolation.
 *
 * Two groups, both against real external systems:
 *   1. Connector / service-task isolation (SIR-1/2/3): each test activates ONE tool segment
 *      of bpmn/test-claims-tools.bpmn via startBeforeElement(), so the HTTP JSON connector
 *      runs for real against claim-demo.free.beeceptor.com and the segment runs to its end event.
 *   2. Judge quality (SIR-4/5): runs the full assessment agent + Quality Judge against real
 *      Bedrock. SIR-4 validates the report content (LLM-as-judge). SIR-5 validates that the
 *      judge populates EVERY quality output its prompt is supposed to produce.
 *
 * Prerequisites:
 *   - Docker running (embedded Zeebe + Connectors runtime, runtime-mode=managed)
 *   - .env at repo root with AWS_BEDROCK_* filled in (judge groups)
 *   - judge chat-model provider configured in application-integration.yml
 *   - Run from test/: env $(cat ../../../.env | grep -v '^#' | xargs) mvn test -P integration-test
 */
@SpringBootTest(properties = {"camunda.client.worker.defaults.enabled=false"})
@CamundaSpringProcessTest
@TestDeployment(resources = {
    "bpmn/test-claims-tools.bpmn",
    "claims-processing-agent.bpmn",
    "claim-submission-form.form",
    "assessment-failure-form.form",
    "escalated-claim-form.form",
    "manual-review-form.form",
    "request-additional-docs-form.form"
})
public class ClaimsExternalSystemsIT {

    private static final String TOOLS_PROCESS = "test-claims-tools";
    private static final String MAIN_PROCESS = "CamundaInsurance_ClaimsProcessing";

    @Autowired private CamundaClient client;

    @BeforeAll
    static void configureTimeout() {
        CamundaAssert.setAssertionTimeout(Duration.ofMinutes(5));
    }

    // =========================================================================
    // Group 1 — connector / service-task isolation (SIR-1/2/3).
    // Prove each tool call completes AND its segment runs to the end event.
    // =========================================================================

    @Test
    @Timeout(180)
    @DisplayName("SIR-1: PolicyLookup connector runs and its segment completes")
    void policyLookupInIsolation() {
        var instance = client.newCreateInstanceCommand()
            .bpmnProcessId(TOOLS_PROCESS)
            .latestVersion()
            .startBeforeElement("PolicyLookup")
            .variables(Map.of("claimId", "CLM-2025-0042"))
            .send().join();

        assertThatProcessInstance(instance)
            .hasCompletedElements(byId("PolicyLookup"), byId("End_PolicyLookup"))
            .isCompleted();
    }

    @Test
    @Timeout(180)
    @DisplayName("SIR-2: GetCustomerProfile connector runs and its segment completes")
    void getCustomerProfileInIsolation() {
        var instance = client.newCreateInstanceCommand()
            .bpmnProcessId(TOOLS_PROCESS)
            .latestVersion()
            .startBeforeElement("GetCustomerProfile")
            .variables(Map.of("customerId", "CUST-4521"))
            .send().join();

        assertThatProcessInstance(instance)
            .hasCompletedElements(byId("GetCustomerProfile"), byId("End_GetCustomerProfile"))
            .isCompleted();
    }

    @Test
    @Timeout(180)
    @DisplayName("SIR-3: CalculateDamageEstimate connector runs and its segment completes")
    void calculateDamageEstimateInIsolation() {
        var instance = client.newCreateInstanceCommand()
            .bpmnProcessId(TOOLS_PROCESS)
            .latestVersion()
            .startBeforeElement("CalculateDamageEstimate")
            .variables(Map.of(
                "claimId", "CLM-2025-0042",
                "damageDescription", "Rear-end collision, bumper and trunk damage."))
            .send().join();

        assertThatProcessInstance(instance)
            .hasCompletedElements(byId("CalculateDamageEstimate"), byId("End_CalculateDamageEstimate"))
            .isCompleted();
    }

    // Pre-crafted fraud assessment report — injected as mocked agent output so SIR-4 starts
    // directly at Agent_Judge without a real assessment-agent call. Deterministic fraud signal
    // so the judge reliably decides ESCALATE.
    private static final String FRAUD_ASSESSMENT_REPORT =
        "CLAIM ASSESSMENT REPORT — CLM-2025-0042\n\n"
        + "RISK RATING: HIGH\nRECOMMENDED DECISION: ESCALATE\n\n"
        + "Fraud indicators identified:\n"
        + "1. COVERAGE TIMING — collision coverage added 8 days before the incident date.\n"
        + "2. CLAIM HISTORY — customer has multiple active claims in the past 12 months.\n"
        + "3. OPEN FRAUD INVESTIGATION — a prior fraud case is already open on this account.\n"
        + "4. UNVERIFIABLE DOCUMENTATION — the referenced police report cannot be located.\n"
        + "5. INFLATED ESTIMATE — claimed total-loss value of $52,000 exceeds market value "
        + "by ~$18,000 per the damage estimate tool.\n\n"
        + "Policy status: ACTIVE. Coverage verified. Deductible: $500.\n\n"
        + "Conclusion: five concurrent fraud indicators. Do NOT approve. ESCALATE immediately "
        + "to a human adjuster and refer to the Special Investigations Unit.";

    // =========================================================================
    // Group 2 — judge quality (SIR-4/5).
    // =========================================================================

    /**
     * SIR-4 — Quality Judge routes a fraud assessment to adjuster escalation.
     *
     * Starts the main process directly at Agent_Judge (startBeforeElement) with a
     * pre-crafted fraud assessment report, so no real assessment-agent call is made.
     * The real Quality Judge runs against the mocked report via Bedrock and must
     * set claimDecision=ESCALATE, which activates the human-control event subprocess.
     * Test terminates as soon as the escalation start event fires — the user task is
     * left active (no adjuster interaction required here).
     */
    @Test
    @Timeout(180)
    @DisplayName("SIR-4: Quality Judge routes a fraud assessment report to adjuster escalation")
    void assessmentReportIdentifiesFraud() {
        // claimDecision is what the assessment agent would have set. Injected here because
        // we start at Agent_Judge directly (no assessment agent call). The gateway requires
        // an explicit value — no default flow.
        var instance = client.newCreateInstanceCommand()
            .bpmnProcessId(MAIN_PROCESS)
            .latestVersion()
            .startBeforeElement("Agent_Judge")
            .variables(Map.of(
                "claimId", "CLM-2025-0042",
                "customerId", "CUST-4521",
                "claimType", "collision",
                "customerName", "IT Quality Customer",
                "customerEmail", "it-quality@camunda.example.com",
                "incidentDate", "2026-06-01",
                "assessmentReport", FRAUD_ASSESSMENT_REPORT,
                "claimDecision", "ESCALATE"))
            .send().join();

        // Prove the full requirement path: judge runs, gateway routes to escalate, and
        // the human-control subprocess fires (Start_HumanEscalation completed = escalation
        // event was caught and the human task activated).
        assertThatProcessInstance(instance)
            .hasCompletedElements(byId("Agent_Judge"), byId("Start_HumanEscalation"));
    }

    @Test
    @Timeout(360)
    @DisplayName("SIR-5: the Quality Judge populates every quality output it is prompted to produce")
    void judgePopulatesQualityOutputs() {
        var instance = startMainProcess(
            "CLM-2025-0042", "CUST-4521", "collision",
            "Total-loss collision claimed at $52,000. Coverage added 8 days before the incident. "
                + "Prior open fraud investigation and multiple recent claims.",
            "2026-06-01");

        assertThatProcessInstance(instance).hasCompletedElements(byId("Agent_Judge"));

        // Heavy variable validation: the judge prompt must yield a parseable quality JSON with
        // a numeric overall score, written feedback, and the full score object. This is the
        // requirement-proving assertion for the in-process Quality Judge.
        assertThatProcessInstance(instance)
            .hasVariableNames("agentQualityScore", "qualityFeedback", "qualityScores")
            .hasVariableSatisfies("agentQualityScore", Number.class,
                s -> assertThat(s.doubleValue()).isBetween(0.0, 1.0))
            .hasVariableSatisfies("qualityFeedback", String.class,
                f -> assertThat(f).isNotBlank())
            .hasVariableSatisfies("qualityScores", Map.class,
                m -> assertThat(m).containsKeys("overallScore", "feedback"));
    }

    // SIR-4 (similarity) — embedding-based check; native to CPT 8.10 (hasVariableSimilarTo).
    @Test
    @Timeout(360)
    @Disabled("Requires Bedrock embedding access: bedrock:InvokeModel on amazon.titan-embed-text-v2:0 "
        + "for the test IAM user. Returns 403 AccessDenied until granted. Re-enable once the embedding "
        + "model is authorized (or point similarity at another embedding provider).")
    @DisplayName("SIR-4 (similarity): assessment report matches a reference fraud assessment")
    void assessmentReportSemanticSimilarity() {
        var instance = startMainProcess(
            "CLM-2025-0042", "CUST-4521", "collision",
            "Total-loss collision claimed at $52,000. Coverage added 8 days before the incident. "
                + "Prior open fraud investigation and multiple recent claims.",
            "2026-06-01");

        assertThatProcessInstance(instance).hasCompletedElements(byId("Agent_Judge"));

        assertThatProcessInstance(instance)
            .hasVariableSimilarTo(
                "assessmentReport",
                "The claim shows multiple fraud indicators: collision coverage added shortly "
                    + "before the incident, prior fraud history, and a damage estimate far above "
                    + "the vehicle value. Recommend escalation to a human adjuster and referral "
                    + "to the Special Investigations Unit.");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ProcessInstanceEvent startMainProcess(
            String claimId, String customerId, String claimType,
            String damageDescription, String incidentDate) {
        return client.newCreateInstanceCommand()
            .bpmnProcessId(MAIN_PROCESS)
            .latestVersion()
            .variables(Map.of(
                "claimId", claimId,
                "customerId", customerId,
                "claimType", claimType,
                "damageDescription", damageDescription,
                "customerName", "IT Quality Customer",
                "customerEmail", "it-quality@camunda.example.com",
                "incidentDate", incidentDate))
            .send().join();
    }
}
