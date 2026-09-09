package io.camunda.tests;

import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import io.camunda.process.test.api.testCases.TestCaseSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "camunda.client.worker.defaults.enabled=false")
@CamundaSpringProcessTest
@TestDeployment(resources = "ai-agent-native-anthropic.bpmn")
class JsonProcessTest {

  @Autowired private TestCaseRunner testCaseRunner;

  @ParameterizedTest
  @TestCaseSource
  void shouldPass(TestCase testCase, String filename) {
    testCaseRunner.run(testCase);
  }
}
