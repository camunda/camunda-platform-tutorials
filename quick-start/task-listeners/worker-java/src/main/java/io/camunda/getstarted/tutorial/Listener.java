package io.camunda.getstarted.tutorial;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CompleteJobResult;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.spring.client.annotation.JobWorker;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class Listener {

  /** Enable this flag to auto deploy the process on startup */
  private static final boolean SHOULD_DEPLOY_PROCESS_ON_STARTUP = false;

  /** Enable this flag to auto create an instance on startup */
  private static final boolean SHOULD_CREATE_PROCESS_INSTANCE_ON_STARTUP = false;

  public static void main(String[] args) {
    SpringApplication.run(Listener.class, args);
  }

  private CamundaClient client;

  @Autowired
  public Listener(CamundaClient client) {
    this.client = client;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void deployProcessAndStartProcessInstance() {
    if (SHOULD_DEPLOY_PROCESS_ON_STARTUP) {
      client
          .newDeployResourceCommand()
          .addResourceFromClasspath("Quick_Start_Task_Listeners.bpmn")
          .execute();
      System.out.println("Process deployed successfully.");
    }
    if (SHOULD_CREATE_PROCESS_INSTANCE_ON_STARTUP) {
      client
          .newCreateInstanceCommand()
          .bpmnProcessId("task-listener-tutorial")
          .latestVersion()
          .variable("assignee", "john")
          .execute();
      System.out.println("Process instance created successfully.");
    }
  }

  @JobWorker(type = "assign_new_task", autoComplete = SHOULD_DEPLOY_PROCESS_ON_STARTUP)
  public void assignNewUserTasks(final ActivatedJob job) {
    final Map<String, Object> variables = job.getVariablesAsMap();
    if (!variables.containsKey("assignee") && !variables.containsKey("manager")) {
      client
          .newFailCommand(job)
          .retries(0)
          .errorMessage(
              "No assignee or manager variable provided. Please set either 'assignee' or 'manager' variable.")
          .execute();
      System.out.println(
          "Job failed: No assignee or manager variable provided. Incident will be raised.");
      return;
    }
    // assign to assignee or manager if no assignee is provided
    final String assignee = (String) variables.getOrDefault("assignee", variables.get("manager"));
    client
        .newCompleteCommand(job)
        .withResult(new CompleteJobResult().correctAssignee(assignee))
        .execute();
    System.out.println("Job completed successfully. Task assigned to: " + assignee);
  }
}
