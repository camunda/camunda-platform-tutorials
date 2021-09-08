package io.berndruecker.oreilly.training;


import org.springframework.stereotype.Component;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;

@Component
public class Worker {

  @ZeebeWorker(type = "orchestrate-something")
  public void celebrate(final JobClient client, final ActivatedJob job) {

      // retrieve a variable from the process instance
      String processVariableA = (String) job.getVariablesAsMap().get("processVariableA");
      String processVariableB = (String) job.getVariablesAsMap().get("processVariableB");

      // Do the business logic
      System.out.println("Yeah, now you can orchestrate something using the data of the process instance: " + processVariableA + " | " + processVariableB);

      // complete the external task
      client.newCompleteCommand(job.getKey()).send()
              .exceptionally((throwable -> {throw new RuntimeException("Could not complete job", throwable);}));
  }

}
