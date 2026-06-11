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
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for claims-processing-agent using real AWS Bedrock.
 *
 * runtime-mode=managed: embedded Zeebe + full Connectors runtime in Docker.
 * The agenticai connector makes real calls to Bedrock. Service tools (PolicyLookup,
 * GetCustomerProfile, CalculateDamageEstimate) call claim-demo.free.beeceptor.com.
 *
 * After each test, the CAPTURE log line shows agent variable output — copy it
 * to the unit test file to refresh mock data on model swaps.
 *
 * Prerequisites:
 *   - Docker running
 *   - .env at repo root with AWS_BEDROCK_* vars filled in
 *   - Run from test/: env $(cat ../../.env | grep -v '^#' | xargs) mvn test -P integration-test
 *
 * Assessment: 3 tool calls + 2 Bedrock roundtrips ≈ 60–120s. @Timeout allows 6 minutes.
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
        // 3 tool calls × ~15s simulated delay + Bedrock latency × 2 (assessor + judge).
        CamundaAssert.setAssertionTimeout(Duration.ofMinutes(5));
    }

    // =========================================================================
    // Test 1 — clean claim
    // Low-value collision, single claimant, no fraud markers.
    // Expected: APPROVE (process completes directly) or MANUAL_REVIEW (adjuster task).
    // =========================================================================

    @Test
    @Timeout(360)
    @DisplayName("clean claim → agent assesses and judge decides, process completes")
    void cleanClaimCompletes() {
        var instance = startProcess(
            "CLM-IT-001", "CUST-IT-001", "collision",
            "Minor rear-end impact at traffic light. Other driver at fault. " +
            "Single claimant. Police report filed. Repair estimate $950.",
            "2026-05-20"
        );

        // Assert both agents ran end-to-end (waits up to assertion timeout)
        assertThatProcessInstance(instance)
            .hasCompletedElements(byId("Agent_ClaimsAssessment"), byId("Agent_Judge"));

        captureAgentOutput(instance.getProcessInstanceKey());

        // Complete any user task the judge triggered (MANUAL_REVIEW or ESCALATE path).
        // If none appears within 20s, the process completed via APPROVE — no action needed.
        completeAnyPendingUserTask(instance.getProcessInstanceKey());

        assertThatProcessInstance(instance).isCompleted();
    }

    // =========================================================================
    // Test 2 — high-fraud claim
    // Multiple hard fraud signals. Expected: ESCALATE → Task_HumanReview active.
    // =========================================================================

    @Test
    @Timeout(360)
    @DisplayName("high-fraud claim → judge escalates, adjuster resolves")
    void highFraudClaimEscalates() {
        var instance = startProcess(
            "CLM-IT-002", "CUST-IT-999", "collision",
            "Total-loss collision. Vehicle allegedly destroyed. Damage $52,000. " +
            "Coverage added 8 days before incident. Customer filed 4 claims this year. " +
            "Police report has conflicting witness accounts. Prior open fraud investigation.",
            "2026-06-01"
        );

        // Assert both agents ran
        assertThatProcessInstance(instance)
            .hasCompletedElements(byId("Agent_ClaimsAssessment"), byId("Agent_Judge"));

        captureAgentOutput(instance.getProcessInstanceKey());

        // Fraud-loaded claim should trigger Task_HumanReview (ESCALATE branch)
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> hasActiveTask(instance.getProcessInstanceKey(), "Task_HumanReview"));

        completeTask(instance.getProcessInstanceKey(), "Task_HumanReview",
            Map.of("adjusterResolution", "DENY", "adjusterNotes", "IT test: fraud confirmed by agent."));

        assertThatProcessInstance(instance).isCompleted();
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
                "incidentDate", incidentDate
            ))
            .send()
            .join();
    }

    /** Waits up to 20s for any user task; completes it if found, skips if none (APPROVE path). */
    private void completeAnyPendingUserTask(long processInstanceKey) {
        List<?> tasks;
        try {
            tasks = Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> activeTasks(processInstanceKey), t -> !t.isEmpty());
        } catch (ConditionTimeoutException e) {
            log.info("No user task after 20s — APPROVE path for instance {}", processInstanceKey);
            return;
        }

        var task = tasks.get(0);
        log.info("Completing user task {} on instance {}", getElementId(task), processInstanceKey);
        completeTask(processInstanceKey, getElementId(task),
            Map.of("adjusterResolution", "APPROVE", "adjusterNotes", "IT test auto-resolution"));
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

    private List<?> activeTasks(long processInstanceKey) {
        return client.newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey).state(UserTaskState.CREATED))
            .send().join().items();
    }

    private String getElementId(Object task) {
        return ((io.camunda.client.api.search.response.UserTask) task).getElementId();
    }

    private void captureAgentOutput(long processInstanceKey) {
        try {
            var tasks = activeTasks(processInstanceKey);
            if (tasks.isEmpty()) {
                log.info("CAPTURE: instance {} — APPROVE path (process completed directly)", processInstanceKey);
            } else {
                log.info("CAPTURE: instance {} — user task active: {}",
                    processInstanceKey, tasks.stream().map(this::getElementId).toList());
            }
        } catch (Exception e) {
            log.warn("Could not capture agent output: {}", e.getMessage());
        }
    }
}
