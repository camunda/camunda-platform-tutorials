package io.camunda.getstarted.tutorial;

import java.util.HashMap;
import java.util.Map;

import io.camunda.zeebe.spring.client.annotation.JobWorker;
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
  
  @JobWorker(type = "orchestrate-something")
  public Map<String, Object> orchestrateSomething(final ActivatedJob job) {

      // Do the business logic
      System.out.println("Yeah, now you can orchestrate something :-) You could use data from the process variables: " + job.getVariables());

      // Probably add some process variables
      HashMap<String, Object> variables = new HashMap<>();
      variables.put("resultValue1", 42);
      return variables;
  }

}
