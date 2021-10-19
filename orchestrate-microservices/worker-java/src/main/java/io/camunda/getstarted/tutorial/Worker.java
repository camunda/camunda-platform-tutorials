package io.camunda.getstarted.tutorial;

import java.util.HashMap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;

@SpringBootApplication
@EnableZeebeClient
public class Worker {

  public static void main(String[] args) {
    SpringApplication.run(Worker.class, args);
  }
  
  @ZeebeWorker(type = "orchestrate-something")
  public void orchestrateSomething(final JobClient client, final ActivatedJob job) {
      // Do the business logic
      System.out.println("Yeah, now you can orchestrate something :-)");

      // Probably read some input/output
      HashMap<String, Object> variables = new HashMap<>();
      variables.put("resultValue1", 42);

      // complete the task in the workflow engine
      client.newCompleteCommand(job.getKey())
        .variables(variables) 
        .send()
        .exceptionally((throwable -> {throw new RuntimeException("Could not complete job", throwable);}));
  }

}
