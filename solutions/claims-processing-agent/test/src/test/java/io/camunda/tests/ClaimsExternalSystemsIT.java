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

    // =========================================================================
    // Group 2 — judge quality (SIR-4/5). Use the known fraud-laden id CLM-2025-0042
    // so the assessment is deterministic enough to validate.
    // =========================================================================

    @Test
    @Timeout(360)
    @DisplayName("SIR-4: the assessment report identifies fraud risk and recommends a decision")
    void assessmentReportIdentifiesFraud() {
        var instance = startMainProcess(
            "CLM-2025-0042", "CUST-4521", "collision",
            "Total-loss collision claimed at $52,000. Coverage added 8 days before the incident. "
                + "Prior open fraud investigation and multiple recent claims.",
            "2026-06-01");

        assertThatProcessInstance(instance).hasCompletedElements(byId("Agent_Judge"));

        // LLM-as-judge on the report content (CPT resolves variable names, not dot-paths).
        assertThatProcessInstance(instance)
            .hasVariableSatisfiesJudge(
                "assessmentReport",
                "identifies fraud risk in the claim and recommends escalating or denying it");
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
