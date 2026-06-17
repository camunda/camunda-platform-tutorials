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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Process integration requirements (PIR) — the whole process end to end on real Bedrock.
 *
 * runtime-mode=managed: embedded Zeebe + full Connectors runtime in Docker. The
 * agentic
 * connector makes real Bedrock calls; service tools call claim-demo.free.beeceptor.com.
 *
 * Each test PROVES one business requirement by asserting the exact path the requirement
 * names (the elements completed through to the terminal end event) plus the routing
 * variable claimDecision — not merely that the process finished.
 *
 * Determinism: the live agent's routing follows the tool data. All three beeceptor fixture
 * sets (fraud/clean/border) return concrete JSON values — verified by CIR-1 through CIR-11.
 * PIR-1 (ESCALATE), PIR-2 (APPROVE), and PIR-3 (MANUAL_REVIEW) are all enabled.
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

    static {
        BedrockIntegrationSupport.prepareSystemPropertiesFromEnvironment();
    }

    @Autowired private CamundaClient client;

    @BeforeAll
    static void configureTimeout() {
        BedrockIntegrationSupport.assumeReady();
        // Assessment agent (multi tool call) + judge, both real Bedrock. CPT default 10s is too
        // short. The assessment agent alone can run several minutes across tool-call rounds, so
        // give the full assessment -> judge -> gateway path headroom. 12 minutes covers the
        // observed range (9–11 min) plus margin for Bedrock latency spikes.
        CamundaAssert.setAssertionTimeout(Duration.ofMinutes(12));
    }

    // =========================================================================
    // PIR-1 — a fraudulent claim is escalated to a human adjuster.
    // Proven by the full ESCALATE path through to End_HumanResolved + claimDecision.
    // Uses the known fraud-laden id CLM-2025-0042 (deterministic with current beeceptor).
    // =========================================================================

    @Test
    @Timeout(800)
    @DisplayName("PIR-1: a fraudulent claim is escalated to a human adjuster")
    void fraudClaimEscalatesToAdjuster() {
        AtomicReference<Integer> observedModelCalls = new AtomicReference<>();
        AtomicReference<Integer> observedInputTokens = new AtomicReference<>();
        AtomicReference<Integer> observedOutputTokens = new AtomicReference<>();

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
        // Optional: capture live token telemetry for report cost estimation.
        assertThatProcessInstance(instance)
            .hasVariableSatisfies("agent", Map.class, a -> {
                accumulateMetrics(a, observedModelCalls, observedInputTokens, observedOutputTokens);
            })
            .hasVariableSatisfies("agentJudge", Map.class, a -> {
                accumulateMetrics(a, observedModelCalls, observedInputTokens, observedOutputTokens);
            });
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

        emitReportValue(
            "PIR-1",
            observedModelCalls.get(),
            observedInputTokens.get(),
            observedOutputTokens.get());
    }

    // =========================================================================
    // PIR-2 — a clean, well-documented claim is approved without human touch.
    // Proven by the APPROVE path through to End_ClaimApproved + claimDecision=APPROVE.
    // Needs a clean-risk fixture for CLM-IT-CLEAN-001 / CUST-IT-CLEAN (see
    // test/BEECEPTOR-FIXTURES.md). @Disabled until that data exists.
    // =========================================================================

    @Test
    @Timeout(800)
    @DisplayName("PIR-2: a clean, well-documented claim is approved without human touch")
    void cleanClaimIsApproved() {
        AtomicReference<Integer> observedModelCalls = new AtomicReference<>();
        AtomicReference<Integer> observedInputTokens = new AtomicReference<>();
        AtomicReference<Integer> observedOutputTokens = new AtomicReference<>();

        var instance = startProcess(
            "CLM-IT-CLEAN-001", "CUST-IT-CLEAN", "collision",
            "Minor rear-end impact at a traffic light. Other driver confirmed at fault. Single "
                + "claimant, active comprehensive policy, clean claims history, no prior claims. "
                + "Body-shop estimate of $950 submitted.",
            "2026-05-20");

        // Phase 1: wait for assessment path to complete (up to 12 min).
        assertCompletedOrDiagnose(instance,
            byId("Agent_ClaimsAssessment"),
            byId("Agent_Judge"),
            byId("Gateway_JudgeDecision"));

        assertThatProcessInstance(instance)
            .hasVariableSatisfies("agent", Map.class, a -> {
                accumulateMetrics(a, observedModelCalls, observedInputTokens, observedOutputTokens);
            })
            .hasVariableSatisfies("agentJudge", Map.class, a -> {
                accumulateMetrics(a, observedModelCalls, observedInputTokens, observedOutputTokens);
            });

        // Trace: PolicyLookup must return fraudRiskScore=low for the clean fixture.
        // If this fails, the agent called GetCustomerProfile with the wrong ID and beeceptor
        // returned a fraud fixture, or the beeceptor clean rule is misconfigured.
        traceVariable(instance, "fraudRiskScore", "low");

        // Phase 2: fail fast — decision is final once the gateway fires.
        assertDecisionFastFail(instance, "APPROVE");

        // Phase 3: confirm terminal element.
        assertCompletedOrDiagnose(instance,
            byId("Start_ClaimForm"),
            byId("Agent_ClaimsAssessment"),
            byId("Agent_Judge"),
            byId("Gateway_JudgeDecision"),
            byId("End_ClaimApproved"));
        assertThatProcessInstance(instance).isCompleted();

        emitReportValue(
            "PIR-2",
            observedModelCalls.get(),
            observedInputTokens.get(),
            observedOutputTokens.get());
    }

    // =========================================================================
    // PIR-3 — an ambiguous claim with no hard fraud signal goes to manual review.
    // Proven by the MANUAL_REVIEW path through to End_ManualResolved + claimDecision.
    // Needs a medium-risk fixture for CLM-IT-BORDER-001 / CUST-IT-BORDER (see
    // test/BEECEPTOR-FIXTURES.md). @Disabled until that data exists.
    // =========================================================================

    @Test
    @Timeout(800)
    @DisplayName("PIR-3: an ambiguous claim with no hard fraud signal goes to manual review")
    void borderlineClaimGoesToManualReview() {
        AtomicReference<Integer> observedModelCalls = new AtomicReference<>();
        AtomicReference<Integer> observedInputTokens = new AtomicReference<>();
        AtomicReference<Integer> observedOutputTokens = new AtomicReference<>();

        var instance = startProcess(
            "CLM-IT-BORDER-001", "CUST-IT-BORDER", "theft",
            "Vehicle stolen from a driveway overnight. No witnesses and no CCTV. Medium risk "
                + "rating, no hard fraud indicators, circumstances are ambiguous.",
            "2026-04-15");

        // Phase 1: wait for assessment path to complete (up to 12 min).
        assertCompletedOrDiagnose(instance,
            byId("Agent_ClaimsAssessment"),
            byId("Agent_Judge"),
            byId("Gateway_JudgeDecision"));

        assertThatProcessInstance(instance)
            .hasVariableSatisfies("agent", Map.class, a -> {
                accumulateMetrics(a, observedModelCalls, observedInputTokens, observedOutputTokens);
            })
            .hasVariableSatisfies("agentJudge", Map.class, a -> {
                accumulateMetrics(a, observedModelCalls, observedInputTokens, observedOutputTokens);
            });

        // Trace: PolicyLookup must return fraudRiskScore=medium for the border fixture.
        // If this fails, the agent used the wrong customerId for GetCustomerProfile and
        // beeceptor returned a different fixture, or the beeceptor border rule is misconfigured.
        traceVariable(instance, "fraudRiskScore", "medium");

        // Phase 2: fail fast — decision is final once the gateway fires.
        assertDecisionFastFail(instance, "MANUAL_REVIEW");

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

        emitReportValue(
            "PIR-3",
            observedModelCalls.get(),
            observedInputTokens.get(),
            observedOutputTokens.get());
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Object root, String... keys) {
        Object current = root;
        for (var key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        if (current instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static Integer nestedInt(Map<String, Object> root, String key) {
        if (root == null) {
            return null;
        }
        var value = root.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static void accumulateMetrics(
            Object variable,
            AtomicReference<Integer> modelCalls,
            AtomicReference<Integer> inputTokens,
            AtomicReference<Integer> outputTokens) {
        var metrics = nestedMap(variable, "context", "metrics");
        if (metrics == null) {
            return;
        }
        addMetric(modelCalls, nestedInt(metrics, "modelCalls"));
        var tokenUsage = nestedMap(metrics, "tokenUsage");
        if (tokenUsage != null) {
            addMetric(inputTokens, nestedInt(tokenUsage, "inputTokenCount"));
            addMetric(outputTokens, nestedInt(tokenUsage, "outputTokenCount"));
        }
    }

    private static void addMetric(AtomicReference<Integer> target, Integer value) {
        if (value == null) {
            return;
        }
        var current = target.get();
        target.set((current == null ? 0 : current) + value);
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\"";
    }

    private static String jsonNumber(Number value) {
        return value == null ? "null" : String.format(Locale.ROOT, "%s", value);
    }

    private static void emitReportValue(
            String id,
            Integer modelCalls,
            Integer inputTokenCount,
            Integer outputTokenCount) {
        var json = "{" +
            "\"id\":" + jsonString(id) + "," +
            "\"modelCalls\":" + jsonNumber(modelCalls) + "," +
            "\"inputTokenCount\":" + jsonNumber(inputTokenCount) + "," +
            "\"outputTokenCount\":" + jsonNumber(outputTokenCount) +
            "}";
        // Parsed by report/generate-report.mjs from surefire system-out.
        System.out.println("REPORT_VALUE " + json);
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

    // =========================================================================
    // Diagnostic helpers — active-element probing + fail-fast routing check
    // =========================================================================

    // Elements that can park the process token: checked on assertion failure to
    // surface where the process is stuck without requiring full element-instance API access.
    private static final List<String> ACTIVE_ELEMENT_PROBES = List.of(
        "Agent_ClaimsAssessment", "Agent_Judge",
        "Task_HumanReview", "Task_ManualReview", "SubProcess_HumanControl"
    );

    /**
     * Asserts completed elements; on failure appends currently-active elements to the
     * error message so the reader immediately knows where the process token is parked.
     */
    private void assertCompletedOrDiagnose(
            ProcessInstanceEvent instance,
            io.camunda.process.test.api.assertions.ElementSelector... selectors) {
        try {
            assertThatProcessInstance(instance).hasCompletedElements(selectors);
        } catch (AssertionError e) {
            var sb = new StringBuilder(e.getMessage());
            appendActiveElements(instance, sb);
            throw new AssertionError(sb.toString(), e);
        }
    }

    /**
     * Checks the routing decision with a 5-second timeout (the decision is already written
     * once Gateway_JudgeDecision has fired, so polling past a few seconds is wasted).
     * On failure appends currently-active elements and rethrows immediately.
     */
    private void assertDecisionFastFail(ProcessInstanceEvent instance, String expected) {
        try {
            CamundaAssert.setAssertionTimeout(Duration.ofSeconds(5));
            assertThatProcessInstance(instance).hasVariable("claimDecision", expected);
        } catch (AssertionError e) {
            var sb = new StringBuilder(e.getMessage());
            appendActiveElements(instance, sb);
            throw new AssertionError(sb.toString(), e);
        } finally {
            CamundaAssert.setAssertionTimeout(Duration.ofMinutes(12));
        }
    }

    /**
     * Asserts a named process variable with a 5-second timeout and a diagnostic message
     * that identifies whether the failure is a beeceptor fixture issue or an agent reasoning
     * issue. Use between Phase 1 (element completion) and Phase 2 (decision check) to trace
     * upstream data changes before the agent makes its routing decision.
     */
    private void traceVariable(ProcessInstanceEvent instance, String name, Object expected) {
        try {
            CamundaAssert.setAssertionTimeout(Duration.ofSeconds(5));
            assertThatProcessInstance(instance).hasVariable(name, expected);
        } catch (AssertionError e) {
            throw new AssertionError(
                "Data trace FAILED for variable '" + name + "' (expected: " + expected + "). "
                    + "Check: (1) beeceptor fixture returns the expected value for this claim/customer ID; "
                    + "(2) the agent called each tool with the correct claimId / customerId. "
                    + "Original: " + e.getMessage(), e);
        } finally {
            CamundaAssert.setAssertionTimeout(Duration.ofMinutes(12));
        }
    }

    private void appendActiveElements(ProcessInstanceEvent instance, StringBuilder sb) {
        sb.append("\n\nActive elements at time of failure:");
        // Use a 2-second window per probe — these elements are either active or not;
        // there is no meaningful "wait" here.
        CamundaAssert.setAssertionTimeout(Duration.ofSeconds(2));
        boolean anyActive = false;
        for (var id : ACTIVE_ELEMENT_PROBES) {
            try {
                assertThatProcessInstance(instance).hasActiveElements(byId(id));
                sb.append("\n  ").append(id).append(" [ACTIVE]");
                anyActive = true;
            } catch (AssertionError ignored) {
                // not active — omit from output to keep the message concise
            }
        }
        if (!anyActive) {
            sb.append("\n  (none of the probed elements are active — process may have ended or errored)");
        }
        // Caller's finally block restores the timeout; set it here so the remainder of the
        // probe loop (if any) also uses 2 s rather than the previous timeout.
        CamundaAssert.setAssertionTimeout(Duration.ofMinutes(12));
    }
}
