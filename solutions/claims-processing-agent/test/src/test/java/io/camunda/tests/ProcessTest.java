package io.camunda.tests;

import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import io.camunda.process.test.impl.testCases.TestCasesReader;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.stream.Stream;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("testCases")
    void shouldPass(final TestCase testCase) {
        testCaseRunner.run(testCase);
    }

    static Stream<Arguments> testCases() throws IOException {
        var reader = new TestCasesReader();
        InputStream is = Objects.requireNonNull(
            ProcessTest.class.getClassLoader()
                .getResourceAsStream("test-cases/CamundaInsurance_ClaimsProcessing.test.json"),
            "test-cases/CamundaInsurance_ClaimsProcessing.test.json not found on classpath"
        );
        return reader.read(is).getTestCases().stream().map(tc -> {
            String display = tc.getName()
                + tc.getDescription().map(d -> ": " + d).orElse("");
            return Arguments.of(Named.of(display, tc));
        });
    }
}
