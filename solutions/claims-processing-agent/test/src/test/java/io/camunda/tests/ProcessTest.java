package io.camunda.tests;

import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import io.camunda.process.test.api.testCases.TestCaseSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CPT process-routing tests for claims-processing-agent.
 *
 * The AHSP (Agent_ClaimsAssessment) and Agent_Judge jobs are mocked with canned
 * output captured from real Bedrock runs. Tests cover all gateway branches,
 * the error boundary on the AHSP, and all inner AHSP tool activations.
 *
 * Run: mvn test  (Docker must be running)
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
public class ProcessTest {

    @Autowired
    private TestCaseRunner testCaseRunner;

    @ParameterizedTest
    @TestCaseSource
    void shouldPass(final TestCase testCase, final String filename) {
        testCaseRunner.run(testCase);
    }
}
