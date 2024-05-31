package io.camunda.zeebe.spring.example;

import io.camunda.zeebe.spring.client.EnableZeebeClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableZeebeClient
@EnableScheduling
public class Application {

  public static void main(final String... args) {
    SpringApplication.run(Application.class, args);
  }

}
