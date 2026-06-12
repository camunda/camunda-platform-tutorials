package io.camunda.tests;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Process integration requirements (PIR) — the whole process end to end on real Bedrock.
 *
 * runtime-mode=managed: embedded Zeebe + full Connectors runtime in Docker. The agentic
 * connector makes real Bedrock calls; service tools call claim-demo.free.beeceptor.com.
 *
 * Each test PROVES one business requirement by asserting the exact path the requirement
 * names (the elements completed through to the terminal end event) plus the routing
 * variable claimDecision — not merely that the process finished.
 *
 * Determinism: the live agent's routing follows the tool data. We do not control the
 * beeceptor mock, so only the FRAUD outcome is provable today (the existing CLM-2025-0042
 * id returns fraud-laden data). The APPROVE and MANUAL_REVIEW requirements are written to
 * spec but @Disabled until the beeceptor owner adds the fixtures described in
 * test/BEECEPTOR-FIXTURES.md; re-enable them once those ids return the documented data.
 *
 * Prerequisites:
 *   - Docker running
 *   - .env at repo root with AWS_BEDROCK_* filled in
 *   - Run from test/: env $(cat ../../../.env | grep -v '^#' | xargs) mvn test -P integration-test
 */
@SpringBootTest(properties = {"camunda.client.worker.defaults.enabled=false"})
@CamundaSpringProcessTest
@TestDeployment(resources = {
    "claims-processing-agent.bpmn",
    "claim-submission-form.form",
    "assessment-failure-form.form",
    "escalated-claim-form.form",
    "manual-review-form.form",
    "request-additional-docs-form.form"
})
public class ClaimsProcessingAgentIT {

    private static final Logger log = LoggerFactory.getLogger(ClaimsProcessingAgentIT.class);

    @Autowired private CamundaClient client;

    @BeforeAll
    static void configureTimeout() {
        // Assessment agent (multi tool call) + judge, both real Bedrock. CPT default 10s is too
        // short. The assessment agent alone can run several minutes across tool-call rounds, so
        // give the full assessment -> judge -> gateway path headroom.
        CamundaAssert.setAssertionTimeout(Duration.ofMinutes(9));
    }

    // =========================================================================
    // PIR-1 — a fraudulent claim is escalated to a human adjuster.
    // Proven by the full ESCALATE path through to End_HumanResolved + claimDecision.
    // Uses the known fraud-laden id CLM-2025-0042 (deterministic with current beeceptor).
    // =========================================================================

    @Test
    @Timeout(660)
    @DisplayName("PIR-1: a fraudulent claim is escalated to a human adjuster")
    void fraudClaimEscalatesToAdjuster() {
        var instance = startProcess(
            "CLM-2025-0042", "CUST-4521", "collision",
            "Total-loss collision claimed at $52,000. Collision coverage added 8 days before the "
                + "incident. Customer has a prior open fraud investigation and multiple recent claims. "
                + "Referenced police report cannot be verified.",
            "2026-06-01");

        // The agent assesses and the judge scores; the escalation reaches the human-control
        // subprocess and parks at the adjuster task. Assert the escalation PATH first (this is
        // the requirement) so a transient variable race does not consume the test timeout.
        assertThatProcessInstance(instance)
            .hasCompletedElements(byId("Agent_ClaimsAssessment"), byId("Agent_Judge"));
        // Diagnostic: surface the live routing variable so a non-ESCALATE / null decision is
        // visible in the failure message instead of timing out silently at the human task.
        assertThatProcessInstance(instance).hasVariable("claimDecision", "ESCALATE");
        Awaitility.await()
            .atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> hasActiveTask(instance.getProcessInstanceKey(), "Task_HumanReview"));
        // escalated-claim-form keys: adjusterResolution (required enum), adjusterSettlementAmount (number),
        // adjusterNotes (required). DENY_FRAUD is the correct resolution for five concurrent fraud indicators.
        completeTask(instance.getProcessInstanceKey(), "Task_HumanReview",
            Map.of(
                "adjusterResolution", "DENY_FRAUD",
                "adjusterSettlementAmount", 0,
                "adjusterNotes", "IT: Five concurrent fraud indicators confirmed — coverage timing, prior open "
                    + "fraud investigation, inflated total-loss estimate, unverifiable police report, multiple "
                    + "active claims. Claim denied. Referred to Special Investigations Unit."));

        // Prove the requirement's full path end to end: assessment -> judge -> escalate ->
        // human control -> resolved.
        //
        // NOTE: Gateway_JudgeDecision has NO default flow — all three branches require an explicit
        // claimDecision value. If the live AHSP job-worker does not surface responseJson.decision,
        // the process will throw a no-matching-condition exception at the gateway and this test
        // will fail. That failure is intentional: it surfaces the connector defect rather than
        // silently masking it via a default branch. The defect is tracked separately.
        //
        // Event_EscalateThrow is intentionally omitted: the interrupting human-control event
        // subprocess catches the escalation, so the throw event TERMINATES rather than completes.
        assertThatProcessInstance(instance)
            .hasCompletedElements(
                byId("Start_ClaimForm"),
                byId("Agent_ClaimsAssessment"),
                byId("Agent_Judge"),
                byId("Gateway_JudgeDecision"),
                byId("SubProcess_HumanControl"),
                byId("Start_HumanEscalation"),
                byId("Task_HumanReview"),
                byId("End_HumanResolved"))
            .isCompleted();
    }

    // =========================================================================
    // PIR-2 — a clean, well-documented claim is approved without human touch.
    // Proven by the APPROVE path through to End_ClaimApproved + claimDecision=APPROVE.
    // Needs a clean-risk fixture for CLM-IT-CLEAN-001 / CUST-IT-CLEAN (see
    // test/BEECEPTOR-FIXTURES.md). @Disabled until that data exists.
    // =========================================================================

    @Test
    @Timeout(360)
    @Disabled("Pending beeceptor fixture for CLM-IT-CLEAN-001/CUST-IT-CLEAN returning clean, "
        + "low-risk data so the agent decides APPROVE. See test/BEECEPTOR-FIXTURES.md. No beeceptor "
        + "access to add it here; re-enable once the owner provisions the fixture.")
    @DisplayName("PIR-2: a clean, well-documented claim is approved without human touch")
    void cleanClaimIsApproved() {
        var instance = startProcess(
            "CLM-IT-CLEAN-001", "CUST-IT-CLEAN", "collision",
            "Minor rear-end impact at a traffic light. Other driver confirmed at fault. Single "
                + "claimant, active comprehensive policy, clean claims history. Body-shop estimate "
                + "of $950 and dashcam footage attached.",
            "2026-05-20");

        assertThatProcessInstance(instance)
            .hasCompletedElements(
                byId("Start_ClaimForm"),
                byId("Agent_ClaimsAssessment"),
                byId("Agent_Judge"),
                byId("Gateway_JudgeDecision"),
                byId("End_ClaimApproved"))
            .hasVariable("claimDecision", "APPROVE")
            .isCompleted();
    }

    // =========================================================================
    // PIR-3 — an ambiguous claim with no hard fraud signal goes to manual review.
    // Proven by the MANUAL_REVIEW path through to End_ManualResolved + claimDecision.
    // Needs a medium-risk fixture for CLM-IT-BORDER-001 / CUST-IT-BORDER (see
    // test/BEECEPTOR-FIXTURES.md). @Disabled until that data exists.
    // =========================================================================

    @Test
    @Timeout(360)
    @Disabled("Pending beeceptor fixture for CLM-IT-BORDER-001/CUST-IT-BORDER returning medium-risk, "
        + "no-hard-fraud data so the agent decides MANUAL_REVIEW. See test/BEECEPTOR-FIXTURES.md.")
    @DisplayName("PIR-3: an ambiguous claim with no hard fraud signal goes to manual review")
    void borderlineClaimGoesToManualReview() {
        var instance = startProcess(
            "CLM-IT-BORDER-001", "CUST-IT-BORDER", "theft",
            "Vehicle stolen from a driveway overnight. No witnesses and no CCTV. Medium risk "
                + "rating, no hard fraud indicators, circumstances are ambiguous.",
            "2026-04-15");

        assertThatProcessInstance(instance)
            .hasCompletedElements(byId("Agent_ClaimsAssessment"), byId("Agent_Judge"))
            .hasVariable("claimDecision", "MANUAL_REVIEW");

        Awaitility.await()
            .atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> hasActiveTask(instance.getProcessInstanceKey(), "Task_ManualReview"));
        // manual-review-form keys: reviewerDecision (required enum), reviewerSettlementAmount (required number),
        // reviewNotes (required). Borderline theft — no hard fraud signal — approved at estimated market value.
        completeTask(instance.getProcessInstanceKey(), "Task_ManualReview",
            Map.of(
                "reviewerDecision", "APPROVE",
                "reviewerSettlementAmount", 11000,
                "reviewNotes", "IT: Ambiguous overnight vehicle theft — no CCTV or witnesses but no hard fraud "
                    + "indicators. Police report on file. Settlement at estimated market value."));

        assertThatProcessInstance(instance)
            .hasCompletedElements(
                byId("Start_ClaimForm"),
                byId("Agent_ClaimsAssessment"),
                byId("Agent_Judge"),
                byId("Gateway_JudgeDecision"),
                byId("Task_ManualReview"),
                byId("End_ManualResolved"))
            .isCompleted();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ProcessInstanceEvent startProcess(
            String claimId, String customerId, String claimType,
            String damageDescription, String incidentDate) {
        return client.newCreateInstanceCommand()
            .bpmnProcessId("CamundaInsurance_ClaimsProcessing")
            .latestVersion()
            .variables(Map.of(
                "claimId", claimId,
                "customerId", customerId,
                "claimType", claimType,
                "damageDescription", damageDescription,
                "customerName", "IT Test Customer",
                "customerEmail", "it-test@camunda.example.com",
                "incidentDate", incidentDate))
            .send()
            .join();
    }

    private void completeTask(long processInstanceKey, String elementId, Map<String, Object> vars) {
        var tasks = client.newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey)
                .elementId(elementId)
                .state(UserTaskState.CREATED))
            .send().join().items();
        if (tasks.isEmpty()) {
            throw new AssertionError("Expected active user task '" + elementId + "' but none found");
        }
        client.newCompleteUserTaskCommand(tasks.get(0).getUserTaskKey())
            .variables(vars)
            .send().join();
    }

    private boolean hasActiveTask(long processInstanceKey, String elementId) {
        return !client.newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey)
                .elementId(elementId)
                .state(UserTaskState.CREATED))
            .send().join().items().isEmpty();
    }
}
