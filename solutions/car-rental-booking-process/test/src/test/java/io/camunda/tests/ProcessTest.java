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
 * Runs JSON process tests from src/test/resources/scenarios/ against an embedded Zeebe engine.
 *
 * Uses the CPT 8.9 instruction-based format (TestCaseRunner). Each instruction explicitly
 * targets a specific element ID, so routing bugs cause test failures.
 *
 * Run with: mvn test   (Docker must be running)
 */
@SpringBootTest
@CamundaSpringProcessTest
@TestDeployment(resources = {"car-rental-booking-process.bpmn", "inspect-car.form", "inspect-returned-car.form", "propose-alternative.form"})
public class ProcessTest {

    @Autowired
    private TestCaseRunner testCaseRunner;

    @ParameterizedTest
    @TestCaseSource(directory = "/scenarios")
    void shouldPass(final TestCase testCase, final String fileName) {
        testCaseRunner.run(testCase);
    }
}
