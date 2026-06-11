package io.camunda.tests;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration A — external systems in isolation.
 *
 * Two groups, both against real external systems:
 *
 *   1. Connector / service-task isolation. Each test activates ONE tool segment of
 *      bpmn/test-claims-tools.bpmn via startBeforeElement(), so the HTTP JSON connector
 *      runs for real against claim-demo.free.beeceptor.com — no agent, no routing.
 *      Verifies connector config, URL templating, and FEEL output mappings per tool.
 *      Scope: assert the connector COMPLETED, not the data values it returned.
 *
 *   2. Agent quality (LLM-as-judge). Runs the full assessment agent against real Bedrock
 *      and asserts the behavioral quality of its report with hasVariableSatisfiesJudge.
 *      This is the quality gate that catches a model regression (e.g. Opus to Haiku)
 *      that still routes correctly but reasons worse.
 *
 * Semantic similarity is expressed as a judge-based equivalence expectation until the
 * pinned CPT release ships native embedding-similarity assertions.
 *
 * Prerequisites:
 *   - Docker running (embedded Zeebe + Connectors runtime, runtime-mode=managed)
 *   - .env at repo root with AWS_BEDROCK_* filled in (agent quality group only)
 *   - A judge chat-model provider configured in application-integration.yml
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
        // Connector startup + real HTTP/Bedrock latency. CPT default of 10s is too short.
        CamundaAssert.setAssertionTimeout(Duration.ofMinutes(5));
    }

    // =========================================================================
    // Group 1 — connector / service-task isolation (SIR-1, SIR-2, SIR-3, SIR-6)
    // =========================================================================

    @Test
    @Timeout(180)
    @DisplayName("SIR-1: PolicyLookup connector returns a policy for a known claim")
    void policyLookupInIsolation() {
        var instance = client.newCreateInstanceCommand()
            .bpmnProcessId(TOOLS_PROCESS)
            .latestVersion()
            .startBeforeElement("PolicyLookup")
            .variables(Map.of("claimId", "CLM-2025-0042"))
            .send().join();

        assertThatProcessInstance(instance)
            .hasCompletedElements(byId("PolicyLookup"))
            .isCompleted();
    }

    @Test
    @Timeout(180)
    @DisplayName("SIR-2: GetCustomerProfile connector returns a profile for a known customer")
    void getCustomerProfileInIsolation() {
        var instance = client.newCreateInstanceCommand()
            .bpmnProcessId(TOOLS_PROCESS)
            .latestVersion()
            .startBeforeElement("GetCustomerProfile")
            .variables(Map.of("customerId", "CUST-4521"))
            .send().join();

        assertThatProcessInstance(instance)
            .hasCompletedElements(byId("GetCustomerProfile"))
            .isCompleted();
    }

    @Test
    @Timeout(180)
    @DisplayName("SIR-3: CalculateDamageEstimate connector returns an estimate for a described loss")
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
            .hasCompletedElements(byId("CalculateDamageEstimate"))
            .isCompleted();
    }

    // =========================================================================
    // Group 2 — agent quality, LLM-as-judge (SIR-4, SIR-5)
    // =========================================================================

    @Test
    @Timeout(360)
    @DisplayName("SIR-4 (judge): assessment report identifies fraud signals and recommends escalation")
    void assessmentReportQualityOnFraudClaim() {
        var instance = startMainProcess(
            "CLM-IT-Q1", "CUST-IT-999", "collision",
            "Total-loss collision, vehicle destroyed. Damage $52,000. Coverage added 8 days "
                + "before incident. 4 claims this year. Prior open fraud investigation.",
            "2026-06-01");

        // Wait until the assessment agent has produced its report.
        assertThatProcessInstance(instance).hasCompletedElements(byId("Agent_ClaimsAssessment"));

        // LLM-as-judge: behavioral quality of the report, not exact text.
        assertThatProcessInstance(instance)
            .hasVariableSatisfiesJudge(
                "agent.responseText",
                "identifies at least two distinct fraud indicators and recommends escalating "
                    + "the claim to a human adjuster");
    }

    @Test
    @Timeout(360)
    @DisplayName("SIR-4 (similarity): assessment report matches a reference fraud assessment")
    void assessmentReportSemanticSimilarity() {
        var instance = startMainProcess(
            "SIR-IT-SIM", "CUST-IT-999", "collision",
            "Total-loss collision, vehicle destroyed. Damage $52,000. Coverage added 8 days "
                + "before incident. 4 claims this year. Prior open fraud investigation.",
            "2026-06-01");

        // assessmentReport is mapped from agent.responseText after the judge task runs.
        assertThatProcessInstance(instance).hasCompletedElements(byId("Agent_Judge"));

        // Embedding-based semantic similarity against a reference assessment (CPT 8.10+).
        assertThatProcessInstance(instance)
            .hasVariableSimilarTo(
                "assessmentReport",
                "The claim shows multiple fraud indicators: collision coverage added shortly "
                    + "before the incident, prior fraud history, and a damage estimate far above "
                    + "the vehicle value. Recommend escalation to a human adjuster and referral "
                    + "to the Special Investigations Unit.");
    }

    @Test
    @Timeout(360)
    @DisplayName("SIR-5: independent judge scores the assessment quality consistently")
    void judgeQualityScoreIsConsistent() {
        var instance = startMainProcess(
            "CLM-IT-Q2", "CUST-IT-001", "collision",
            "Minor rear-end impact at a traffic light. Other driver at fault. Single claimant. "
                + "Police report filed. Repair estimate $950.",
            "2026-05-20");

        assertThatProcessInstance(instance).hasCompletedElements(byId("Agent_Judge"));

        // The judge's feedback should reflect a complete, well-supported assessment.
        assertThatProcessInstance(instance)
            .hasVariableSatisfiesJudge(
                "qualityFeedback",
                "describes the assessment as complete and its decision as well supported, "
                    + "without flagging missing tool calls");
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
