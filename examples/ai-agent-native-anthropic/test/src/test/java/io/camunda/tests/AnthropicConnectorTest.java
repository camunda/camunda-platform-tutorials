package io.camunda.tests;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.Testcontainers;
import org.wiremock.spring.EnableWireMock;

@EnableWireMock
@SpringBootTest(
    classes = TestApplication.class,
    properties = {
      "camunda.client.worker.defaults.enabled=false",
      "camunda.process-test.runtime-mode=managed",
      "camunda.process-test.connectors-enabled=true",
      "camunda.process-test.camunda-docker-image-name=camunda/camunda",
      "camunda.process-test.camunda-docker-image-version=8.10.0-alpha5",
      "camunda.process-test.connectors-docker-image-name=camunda/connectors-bundle",
      "camunda.process-test.connectors-docker-image-version=8.10.0-alpha5",
      "camunda.process-test.connectors-secrets.ANTHROPIC_API_KEY=test-api-key",
      "camunda.process-test.connectors-secrets.ANTHROPIC_API_ENDPOINT="
          + "http://host.testcontainers.internal:${wiremock.server.port}"
    })
@CamundaSpringProcessTest
@TestDeployment(resources = "ai-agent-native-anthropic.bpmn")
class AnthropicConnectorTest {

  private static final String MESSAGES_PATH = "/v1/messages";
  private static final String CONVERSATION = "native-anthropic-conversation";

  private static final String TOOL_USE_SSE =
      """
      event: message_start
      data: {"type":"message_start","message":{"id":"msg_tool","type":"message","role":"assistant","model":"claude-sonnet-4-6","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":1200,"output_tokens":0,"cache_creation_input_tokens":1100,"cache_read_input_tokens":0}}}

      event: content_block_start
      data: {"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_policy_1","name":"LookupResolutionPolicy","input":{},"caller":{"type":"direct"}}}

      event: content_block_delta
      data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\\"category\\":\\"billing\\",\\"severity\\":4}"}}

      event: content_block_stop
      data: {"type":"content_block_stop","index":0}

      event: message_delta
      data: {"type":"message_delta","delta":{"stop_reason":"tool_use","stop_sequence":null},"usage":{"input_tokens":1200,"output_tokens":30}}

      event: message_stop
      data: {"type":"message_stop"}

      """;

  private static final String FINAL_RESPONSE_SSE =
      """
      event: message_start
      data: {"type":"message_start","message":{"id":"msg_final","type":"message","role":"assistant","model":"claude-sonnet-4-6","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":1350,"output_tokens":0,"cache_creation_input_tokens":0,"cache_read_input_tokens":1100}}}

      event: content_block_start
      data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

      event: content_block_delta
      data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"{\\"outcome\\":\\"resolved\\",\\"priority\\":\\"high\\",\\"recommendation\\":\\"Verify the invoice and issue a correction.\\",\\"rationale\\":\\"The billing policy marks severity 4 as high priority.\\"}"}}

      event: content_block_stop
      data: {"type":"content_block_stop","index":0}

      event: message_delta
      data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":72}}

      event: message_stop
      data: {"type":"message_stop"}

      """;

  @Autowired private CamundaClient client;

  @Value("${wiremock.server.port}")
  private int wireMockPort;

  @BeforeAll
  static void configureTimeout() {
    CamundaAssert.setAssertionTimeout(Duration.ofMinutes(2));
  }

  @BeforeEach
  void exposeWireMock() {
    Testcontainers.exposeHostPorts(wireMockPort);
  }

  @Test
  void executesNativeAnthropicToolLoopAndMapsResponseAndUsage() {
    stubFor(
        post(urlPathEqualTo(MESSAGES_PATH))
            .inScenario(CONVERSATION)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(sseResponse(TOOL_USE_SSE))
            .willSetStateTo("tool-completed"));
    stubFor(
        post(urlPathEqualTo(MESSAGES_PATH))
            .inScenario(CONVERSATION)
            .whenScenarioStateIs("tool-completed")
            .willReturn(sseResponse(FINAL_RESPONSE_SSE)));

    var instance = startCase();

    assertThatProcessInstance(instance)
        .hasCompletedElements(
            byId("ResolveCaseAgent"), byId("LookupResolutionPolicy"), byId("CaseResolved"))
        .hasVariableSatisfies(
            "agent",
            Map.class,
            rawAgent -> {
              var agent = castMap(rawAgent);
              assertThat(castMap(agent.get("responseJson")))
                  .containsEntry("outcome", "resolved")
                  .containsEntry("priority", "high");
              assertThat(castMap(castMap(agent.get("context")).get("metrics"))).isNotEmpty();
            })
        .isCompleted();

    verify(
        2,
        postRequestedFor(urlPathEqualTo(MESSAGES_PATH))
            .withHeader("x-api-key", equalTo("test-api-key"))
            .withHeader("Accept", equalTo("text/event-stream"))
            .withRequestBody(matchingJsonPath("$.model", equalTo("claude-sonnet-4-6")))
            .withRequestBody(matchingJsonPath("$.thinking.type", equalTo("adaptive")))
            .withRequestBody(matchingJsonPath("$.thinking.display", equalTo("summarized")))
            .withRequestBody(matchingJsonPath("$.output_config.effort", equalTo("high")))
            .withRequestBody(matchingJsonPath("$.cache_control.type", equalTo("ephemeral"))));
    verify(
        postRequestedFor(urlPathEqualTo(MESSAGES_PATH))
            .withRequestBody(
                matchingJsonPath(
                    "$.tools[?(@.name == 'LookupResolutionPolicy')]")));
    verify(
        postRequestedFor(urlPathEqualTo(MESSAGES_PATH))
            .withRequestBody(
                matchingJsonPath(
                    "$.messages[*].content[?(@.type == 'tool_result' && @.tool_use_id == 'toolu_policy_1')]")));
  }

  @Test
  void routesAuthenticationFailureToExplicitFailureEnd() {
    stubFor(
        post(urlPathEqualTo(MESSAGES_PATH))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}
                        """)));

    assertThatProcessInstance(startCase())
        .hasCompletedElements(byId("AgentFailureBoundary"), byId("AgentFailed"))
        .isCompleted();
    verify(
        postRequestedFor(urlPathEqualTo(MESSAGES_PATH))
            .withHeader("x-api-key", equalTo("test-api-key")));
  }

  @Test
  void routesRateLimitFailureToExplicitFailureEnd() {
    stubFor(
        post(urlPathEqualTo(MESSAGES_PATH))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"type":"error","error":{"type":"rate_limit_error","message":"slow down"}}
                        """)));

    assertThatProcessInstance(startCase())
        .hasCompletedElements(byId("AgentFailureBoundary"), byId("AgentFailed"))
        .isCompleted();
  }

  @Test
  void routesMalformedStreamToExplicitFailureEnd() {
    stubFor(
        post(urlPathEqualTo(MESSAGES_PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody("event: message_start\ndata: {not-json}\n\n")));

    assertThatProcessInstance(startCase())
        .hasCompletedElements(byId("AgentFailureBoundary"), byId("AgentFailed"))
        .isCompleted();
  }

  private io.camunda.client.api.response.ProcessInstanceEvent startCase() {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId("ai-agent-native-anthropic")
        .latestVersion()
        .variables(
            Map.of(
                "caseId", "CASE-1",
                "caseRequest", "Resolve a duplicate billing charge with severity 4.",
                "policyContext", "Follow the returned resolution policy."))
        .send()
        .join();
  }

  private static ResponseDefinitionBuilder sseResponse(String body) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "text/event-stream")
        .withBody(body);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }
}
