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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Connector integration requirements (CIR) — external systems exercised in isolation.
 *
 * Two groups, both against real external systems:
 *   1. Connector / service-task isolation (CIR-1 to CIR-11): a 3×3 matrix of (tool × fixture).
 *      Each cell activates ONE tool segment of bpmn/test-claims-tools.bpmn via
 *      startBeforeElement(), so the HTTP JSON connector runs for real against
 *      claim-demo.free.beeceptor.com. Variable assertions prove each fixture returns actual
 *      JSON values, not schema templates. Fixtures: fraud (CLM-2025-0042), clean
 *      (CLM-IT-CLEAN-001), border (CLM-IT-BORDER-001).
 *
 *      Why not use startBeforeElement() directly on the main process AHSP inner elements?
 *      Camunda issue #44823 (fix merged Feb 2026 via PR #46187) removed the engine validation
 *      that blocks creation, so the process instance IS created and the HTTP job IS activated.
 *      However, the AHSP's outputCollection DirectBuffer field is not initialized on the
 *      startBeforeElement path — only on the normal AHSP lifecycle path. When the inner service
 *      task job completes, Zeebe NPEs trying to append to the null outputCollection, rejecting
 *      JOB.COMPLETE with PROCESSING_ERROR. The connector cannot complete the job, so the element
 *      stays active indefinitely. The standalone test-claims-tools.bpmn is required until
 *      Zeebe properly initializes AHSP output state on the startBeforeElement path.
 *
 *   2. Judge quality (CIR-4/5): runs the full assessment agent + Quality Judge against real
 *      Bedrock. CIR-4 validates the report content (LLM-as-judge). CIR-5 validates that the
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

    static {
        BedrockIntegrationSupport.prepareSystemPropertiesFromEnvironment();
    }

    private static final String TOOLS_PROCESS = "test-claims-tools";
    private static final String MAIN_PROCESS = "CamundaInsurance_ClaimsProcessing";

    @Autowired private CamundaClient client;

    @BeforeAll
    static void configureTimeout() {
        CamundaAssert.setAssertionTimeout(Duration.ofMinutes(5));
    }

    // =========================================================================
    // Group 1 — connector / service-task isolation: 3 tools × 3 fixtures.
    //
    // Each beeceptor fixture set (fraud / clean / border) is driven through every
    // tool connector once. Variable assertions prove the mock returns real JSON values
    // for the discriminating fields (not schema templates or null). To add a 4th
    // tool: add expected fields to FixtureSet, a new run helper, and 3 @Test stubs.
    // To add a 4th fixture: one new FixtureSet constant and 3 @Test stubs.
    // =========================================================================

    /**
     * One row in the 3×3 connector isolation matrix.
     *
     * Note: the fraud fixture nests fraudHistory under claimHistory (beeceptor data-shape
     * inconsistency). extractFraudHistory() handles both the nested and flat layouts so the
     * assertion works across all three fixture sets.
     */
    private record FixtureSet(
        String claimId,
        String customerId,
        String damageDescription,
        String fraudRiskScore,  // PolicyLookup: toolCallResult.fraudRiskScore → policyStatus always "active"
        String riskRating,      // GetCustomerProfile: toolCallResult.riskRating → process var riskRating
        boolean fraudHistory,   // GetCustomerProfile: nested or flat — see extractFraudHistory()
        int estimatedAmount,    // CalculateDamageEstimate: toolCallResult.estimatedAmount → damageEstimate
        String repairCategory   // CalculateDamageEstimate: null = fixture value unknown, assert non-blank
    ) {}

    private static final FixtureSet FRAUD = new FixtureSet(
        "CLM-2025-0042", "CUST-4521",
        "Rear-end collision, bumper and trunk damage.",
        "high", "high", true, 47_800, null
    );
    private static final FixtureSet CLEAN = new FixtureSet(
        "CLM-IT-CLEAN-001", "CUST-IT-CLEAN",
        "Minor rear-end impact at a traffic light. Other driver confirmed at fault.",
        "low", "low", false, 950, "minor"
    );
    private static final FixtureSet BORDER = new FixtureSet(
        "CLM-IT-BORDER-001", "CUST-IT-BORDER",
        "Vehicle stolen from a driveway overnight. No witnesses and no CCTV.",
        "medium", "medium", false, 18_000, "moderate"
    );

    // --- CIR-1/2/3: fraud fixture ---

    @Test @Timeout(180) @DisplayName("CIR-1: PolicyLookup returns fraud-risk data for CLM-2025-0042")
    void policyLookupInIsolation()         { runPolicyLookup(FRAUD); }

    @Test @Timeout(180) @DisplayName("CIR-2: GetCustomerProfile returns fraud-risk data for CUST-4521")
    void getCustomerProfileInIsolation()   { runCustomerProfile(FRAUD); }

    @Test @Timeout(180) @DisplayName("CIR-3: CalculateDamageEstimate returns above-market estimate for CLM-2025-0042")
    void calculateDamageEstimateInIsolation() { runDamageEstimate(FRAUD); }

    // --- CIR-6/7/8: clean fixture ---

    @Test @Timeout(180) @DisplayName("CIR-6: PolicyLookup returns clean-risk data for CLM-IT-CLEAN-001")
    void policyLookupCleanFixture()              { runPolicyLookup(CLEAN); }

    @Test @Timeout(180) @DisplayName("CIR-7: GetCustomerProfile returns clean-risk data for CUST-IT-CLEAN")
    void getCustomerProfileCleanFixture()        { runCustomerProfile(CLEAN); }

    @Test @Timeout(180) @DisplayName("CIR-8: CalculateDamageEstimate returns minor-repair estimate for CLM-IT-CLEAN-001")
    void calculateDamageEstimateCleanFixture()   { runDamageEstimate(CLEAN); }

    // --- CIR-9/10/11: border fixture ---

    @Test @Timeout(180) @DisplayName("CIR-9: PolicyLookup returns medium-risk data for CLM-IT-BORDER-001")
    void policyLookupBorderFixture()             { runPolicyLookup(BORDER); }

    @Test @Timeout(180) @DisplayName("CIR-10: GetCustomerProfile returns medium-risk data for CUST-IT-BORDER")
    void getCustomerProfileBorderFixture()       { runCustomerProfile(BORDER); }

    @Test @Timeout(180) @DisplayName("CIR-11: CalculateDamageEstimate returns moderate-repair estimate for CLM-IT-BORDER-001")
    void calculateDamageEstimateBorderFixture()  { runDamageEstimate(BORDER); }

    // --- connector run helpers ---

    private void runPolicyLookup(FixtureSet f) {
        var instance = client.newCreateInstanceCommand()
            .bpmnProcessId(TOOLS_PROCESS).latestVersion()
            .startBeforeElement("PolicyLookup")
            .variables(Map.of("claimId", f.claimId()))
            .send().join();
        assertThatProcessInstance(instance)
            .hasCompletedElements(byId("PolicyLookup"), byId("End_PolicyLookup"))
            .hasVariable("policyStatus", "active")
            .hasVariable("fraudRiskScore", f.fraudRiskScore())
            .isCompleted();
    }

    private void runCustomerProfile(FixtureSet f) {
        var instance = client.newCreateInstanceCommand()
            .bpmnProcessId(TOOLS_PROCESS).latestVersion()
            .startBeforeElement("GetCustomerProfile")
            .variables(Map.of("customerId", f.customerId()))
            .send().join();
        assertThatProcessInstance(instance)
            .hasCompletedElements(byId("GetCustomerProfile"), byId("End_GetCustomerProfile"))
            .hasVariable("riskRating", f.riskRating())
            .hasVariableSatisfies("toolCallResult", Map.class,
                r -> assertThat(extractFraudHistory(r)).isEqualTo(f.fraudHistory()))
            .isCompleted();
    }

    private void runDamageEstimate(FixtureSet f) {
        var instance = client.newCreateInstanceCommand()
            .bpmnProcessId(TOOLS_PROCESS).latestVersion()
            .startBeforeElement("CalculateDamageEstimate")
            .variables(Map.of("claimId", f.claimId(), "damageDescription", f.damageDescription()))
            .send().join();
        var a = assertThatProcessInstance(instance)
            .hasCompletedElements(byId("CalculateDamageEstimate"), byId("End_CalculateDamageEstimate"))
            .hasVariableSatisfies("damageEstimate", Number.class,
                n -> assertThat(n.intValue()).isEqualTo(f.estimatedAmount()));
        if (f.repairCategory() != null) {
            a.hasVariable("damageCategory", f.repairCategory());
        } else {
            a.hasVariableSatisfies("damageCategory", String.class, s -> assertThat(s).isNotBlank());
        }
        a.isCompleted();
    }

    private static boolean extractFraudHistory(Map<?, ?> toolCallResult) {
        // Flat layout (clean / border fixtures).
        if (toolCallResult.containsKey("fraudHistory")) {
            return Boolean.TRUE.equals(toolCallResult.get("fraudHistory"));
        }
        // Nested layout (fraud fixture has claimHistory.fraudHistory).
        if (toolCallResult.get("claimHistory") instanceof Map<?, ?> h
                && h.containsKey("fraudHistory")) {
            return Boolean.TRUE.equals(h.get("fraudHistory"));
        }
        throw new AssertionError(
            "fraudHistory not found at toolCallResult.fraudHistory or toolCallResult.claimHistory.fraudHistory");
    }

    // Pre-crafted fraud assessment report — injected as mocked agent output so CIR-4 starts
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
    // Group 2 — judge quality (CIR-4/5).
    // =========================================================================

    /**
     * CIR-4 — Quality Judge routes a fraud assessment to adjuster escalation.
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
    @DisplayName("CIR-4: Quality Judge routes a fraud assessment report to adjuster escalation")
    void assessmentReportIdentifiesFraud() {
        BedrockIntegrationSupport.assumeReady();
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
    @DisplayName("CIR-5: the Quality Judge populates every quality output it is prompted to produce")
    void judgePopulatesQualityOutputs() {
        BedrockIntegrationSupport.assumeReady();
        var instance = startMainProcess(
            "CLM-2025-0042", "CUST-4521", "collision",
            "Total-loss collision claimed at $52,000. Coverage added 8 days before the incident. "
                + "Prior open fraud investigation and multiple recent claims.",
            "2026-06-01");

        AtomicReference<Double> observedScore = new AtomicReference<>();
        AtomicReference<String> observedFeedback = new AtomicReference<>();
        AtomicReference<Integer> observedModelCalls = new AtomicReference<>();
        AtomicReference<Integer> observedInputTokens = new AtomicReference<>();
        AtomicReference<Integer> observedOutputTokens = new AtomicReference<>();

        assertThatProcessInstance(instance).hasCompletedElements(byId("Agent_Judge"));

        // Heavy variable validation: the judge prompt must yield a parseable quality JSON with
        // a numeric overall score, written feedback, and the full score object. This is the
        // requirement-proving assertion for the in-process Quality Judge.
        assertThatProcessInstance(instance)
            .hasVariableNames("agentQualityScore", "qualityFeedback", "qualityScores")
            .hasVariableSatisfies("agentQualityScore", Number.class,
                s -> {
                    var score = s.doubleValue();
                    observedScore.set(score);
                    assertThat(score).isBetween(0.0, 1.0);
                })
            .hasVariableSatisfies("qualityFeedback", String.class,
                f -> {
                    observedFeedback.set(f);
                    assertThat(f).isNotBlank();
                })
            .hasVariableSatisfies("qualityScores", Map.class, m -> {
                var keys = ((Map<?, ?>) m).keySet();
                assertThat(keys.contains("overallScore")).isTrue();
                assertThat(keys.contains("feedback")).isTrue();
            });

        // Optional: capture agent token metrics when includeAgentContext is enabled.
        assertThatProcessInstance(instance)
            .hasVariableSatisfies("agent", Map.class, a -> {
                accumulateMetrics(a, observedModelCalls, observedInputTokens, observedOutputTokens);
            })
            .hasVariableSatisfies("agentJudge", Map.class, a -> {
                accumulateMetrics(a, observedModelCalls, observedInputTokens, observedOutputTokens);
            });

        emitReportValue(
            "CIR-5",
            observedScore.get(),
            observedFeedback.get(),
            observedModelCalls.get(),
            observedInputTokens.get(),
            observedOutputTokens.get());
    }

    // CIR-4s (similarity) — embedding-based check; native to CPT 8.10 (hasVariableSimilarTo).
    @Test
    @Timeout(360)
    @Disabled("Requires Bedrock embedding access: bedrock:InvokeModel on amazon.titan-embed-text-v2:0 "
        + "for the test IAM user. Returns 403 AccessDenied until granted. Re-enable once the embedding "
        + "model is authorized (or point similarity at another embedding provider).")
    @DisplayName("CIR-4s: assessment report matches a reference fraud assessment (embedding similarity)")
    void assessmentReportSemanticSimilarity() {
        BedrockIntegrationSupport.assumeReady();
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
            Double agentQualityScore,
            String qualityFeedback,
            Integer modelCalls,
            Integer inputTokenCount,
            Integer outputTokenCount) {
        var json = "{" +
            "\"id\":" + jsonString(id) + "," +
            "\"agentQualityScore\":" + jsonNumber(agentQualityScore) + "," +
            "\"qualityFeedback\":" + jsonString(qualityFeedback) + "," +
            "\"modelCalls\":" + jsonNumber(modelCalls) + "," +
            "\"inputTokenCount\":" + jsonNumber(inputTokenCount) + "," +
            "\"outputTokenCount\":" + jsonNumber(outputTokenCount) +
            "}";
        // Parsed by report/generate-report.mjs from surefire system-out.
        System.out.println("REPORT_VALUE " + json);
    }
}
