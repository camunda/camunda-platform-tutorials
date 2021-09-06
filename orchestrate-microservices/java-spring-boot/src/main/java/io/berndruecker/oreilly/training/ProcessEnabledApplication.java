package io.berndruecker.oreilly.training;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.annotation.ZeebeDeployment;

@SpringBootApplication
@EnableZeebeClient
@ZeebeDeployment(resources = "classpath:*.bpmn")
public class ProcessEnabledApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessEnabledApplication.class, args);
    }

}