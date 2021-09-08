# Orchestrate Microservices By Providing a Spring Boot Application as Process Solution

The self-contained process solution contains your microservices code alongside

* The process model as BPMN (auto-deployed during startup)
* Glue code for the service task
* REST endpoint that then starts a process instance

Walkthrough and Thoughts: https://drive.google.com/file/d/12llz457OOmkzPxv0gQhsdkGtjgoBpZDm/view?usp=sharing

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

```
curl -i -X PUT http://localhost:8080/orchestrate
```

* You should see something like this:

```
...
2021-09-06 15:00:59.993  INFO 25084 --- [lication.main()] i.c.z.s.c.c.p.DeploymentPostProcessor    : deployment: ZeebeDeploymentValue{resources=[classpath:*.bpmn], beanInfo=io.camunda.zeebe.spring.client.bean.ClassInfo@6a59b3fd}
2021-09-06 15:01:00.002  INFO 25084 --- [lication.main()] i.c.z.s.c.c.p.ZeebeWorkerPostProcessor   : zeebeWorker: io.camunda.zeebe.spring.client.bean.ClassInfo@3f43db4f       
2021-09-06 15:01:02.828  INFO 25084 --- [lication.main()] i.c.z.s.c.c.p.DeploymentPostProcessor    : Deployed: <microservice-orchestration-tutorial:1>
2021-09-06 15:01:02.836  INFO 25084 --- [lication.main()] i.c.z.s.c.c.p.ZeebeWorkerPostProcessor   : register job worker: io.camunda.zeebe.spring.client.bean.value.ZeebeWorkerValue@32398d1e
2021-09-06 15:01:02.867  INFO 25084 --- [lication.main()] o.s.b.web.embedded.netty.NettyWebServer  : Netty started on port(s): 8080
2021-09-06 15:01:02.885  INFO 25084 --- [lication.main()] i.b.o.t.ProcessEnabledApplication        : Started ProcessEnabledApplication in 3.983 seconds (JVM running for 6.634)
2021-09-06 15:01:32.104  INFO 25084 --- [ault-executor-0] io.camunda.zeebe.client.job.poller       : Activated 1 jobs for worker default and job type orchestrate-something
Yeah, now you can orchestrate something using the data of the process instance: Some data I could read from the REST request | fd48f78b-f8fc-45f5-9ca9-afc6d9d860da
```

