package io.berndruecker.oreilly.training;

import java.util.HashMap;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;

@RestController
public class RestEndpoint {
    
    @Autowired
    private ZeebeClient client;

    @PutMapping("/orchestrate")
    public ResponseEntity<String> startOrchestration(ServerWebExchange exchange) {
        // add data to the process instance
        HashMap<String, Object> variables = new HashMap<String, Object>();
        variables.put("processVariableA", "Some data I could read from the REST request"); 
        variables.put("processVariableB", UUID.randomUUID().toString()); 

        // start the process instance within Zeebe using a blocking call
        ProcessInstanceEvent processInstance = client.newCreateInstanceCommand()
            .bpmnProcessId("microservice-orchestration-tutorial")
            .latestVersion()
            .variables(variables)
            .send().join();

        // And just return something for the sake of the example
        return ResponseEntity
            .status(HttpStatus.OK)
            .body("Started process instance with key: " + processInstance.getProcessInstanceKey());
    }
}
