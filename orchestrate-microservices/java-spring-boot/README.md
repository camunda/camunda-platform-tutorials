# External Task Worker / Java

* Requirements:
  * Java >= 8
  * Maven

* Download/clone the code in this folder.

* You might need to adjust the task type name in the file `Worker.java`. In the demo code it is `celebrate`.
* You need to set your Camunda cloud client connection details in the file `application.properties`. Simply replace the existing sample values.

* Run the worker:

```
mvn package exec:java
```

* You should see something like this:

```
2021-06-28 14:55:03.272  INFO 7720 --- [g.Worker.main()] i.c.z.s.c.c.p.ZeebeWorkerPostProcessor   : zeebeWorker: io.camunda.zeebe.spring.client.bean.ClassInfo@4d68f34
b
2021-06-28 14:55:03.779  INFO 7720 --- [g.Worker.main()] i.c.z.s.c.c.p.ZeebeWorkerPostProcessor   : register job worker: io.camunda.zeebe.spring.client.bean.value.Zee
beWorkerValue@7fcab89e
2021-06-28 14:55:03.786  INFO 7720 --- [g.Worker.main()] io.berndruecker.oreilly.training.Worker  : Started Worker in 1.469 seconds (JVM running for 4.018)
2021-06-28 14:55:39.610  INFO 7720 --- [ault-executor-0] io.camunda.zeebe.client.job.poller       : Activated 1 jobs for worker default and job type celebrate
Yeah, your request was approved and can now be ordered! Please celebrate accordingly!
```

