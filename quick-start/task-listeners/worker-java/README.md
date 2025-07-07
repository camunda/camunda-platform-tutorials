# Task Listeners: A Spring Boot Worker

This project contains a worker that can connect a BPMN user task to set the assignee externally using a creating task
listener.

Requirements:

* Java >= 21
* Maven

To get started:

* Deploy [`Quick_Start_Task_Listeners.bpmn`](src/main/resources/Quick_Start_Task_Listeners.bpmn).
* Start a new instance of this process with a variable `assignee` or `manager`.
* Notice that there is no task in Tasklist yet.
* Notice that the creating listener is active in Operate.
* After running the application, notice that the listener is completed in Operate and that the user task exists in
  Tasklist. Also notice that the task is assigned to the assignee or manager that you provided.

How to run:

* Download/clone the code in this folder.
* You need to set your Camunda cloud client connection details in the file `application.properties`. Simply replace the
  existing sample values.
* Run the listener application to deploy the process, start an instance, and start the listener:

```sh
mvn package exec:java
```

* Check tasklist, and notice that the task is assigned to `john`.
* Check Operate, and notice that the user task reports a creating listener that was completed successfully.

You can now play around with it, to build your understanding of task listeners:

* Stop the listener application.
* Start a new instance of the process, and notice that the task does not appear in Tasklist.
* Check Operate, and notice that a creating listener is active.
* Restart the listener application and notice that the listener is failed and an incident is raised.
* Set a variable `assignee` or `manager` in the process instance, and resolve the incident.
* Check Tasklist, and notice that the task is assigned to the assignee or manager that you provided.