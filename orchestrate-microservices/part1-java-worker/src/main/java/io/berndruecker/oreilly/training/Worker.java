package io.berndruecker.oreilly.training;

import java.util.HashMap;
import io.camunda.zeebe.client.ZeebeClient;

public class Worker {

  public static void main(String[] args) {
    ZeebeClient client = ZeebeClient.newCloudClientBuilder()
      .withClusterId("365eed98-16c1-4096-bb57-eb8828ed131e")
      .withClientId("GZVO3ALYy~qCcD3MYq~sf0GIszNzLE_z")
      .withClientSecret(".RPbZc6q0d6uzRbB4LW.B8lCpsxbBEpmBX0AHQGzINf3.KK9RkzZW1aDaZ-7WYNJ")
      .withRegion("bru-2")
      .build();
    
      client.newWorker().jobType("orchestrate-something")
        .handler((jobClient, job) -> {
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
        }).open();
    }

}
