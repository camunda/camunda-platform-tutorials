# Orchestrate Microservices By A Spring Boot Worker

This project contains a worker that can connect a BPMN service task to whatever you need to.

Requirements:

* Java >= 8
* Maven

How to run:

* Download/clone the code in this folder.
* You need to set your Camunda cloud client connection details in the file `application.properties`. Simply replace the existing sample values.
* Run the application:

```
mvn package exec:java
```

Now you need to model and deploy a BPMN process that contains a service task of type `orchestrate-something`. Start a new instance of this process instance and you will see the sysout of this worker.

Possible extensions:

- Start the worker application multiple times and you will see that all of them serve some traffic
- Create a comparable worker in another programming language following one of the [Get Started Guides](https://github.com/camunda-cloud/camunda-cloud-get-started)
