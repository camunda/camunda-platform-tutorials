# Bank: Loan Origination and Processing

**Technical level:** Medium
<br>
**Industry:** Financial services
<br>
**Use Cases:** Application Handling/Human Task Orchestration

## Summary

This blueprint shows a loan processing process with the loan originating from a customer's interaction with a chatbot. The Camunda process is started when various information about the customer and the loan details are present, this is simulated via the Camunda form on the start event.

The process starts with a check of the credit score in a process-external system: the FICO agencies. With all necessary data present, the data is validated and a ticket is created in the bank's IT systems. As a next step a subprocess is started, with the first step being a business rule task to evaluate the loan request's details. When the loan is automatically approved or declined, the customer is informed directly via the chatbot. In various scenarios, underwriting is necessary.

When the loan is approved automatically or manually, an offer is sent to the customer via mail. At this point, the process waits for the customer's response. Timer events handle reminders and a timeout functionality. When the loan is accepted, the loan provision is handled via the bank's IT systems, and the customer is informed via email.

This blueprint can be easily adapted to be used with different incoming channels. It provides great visibility to the different milestones using intermediate events. Various KPIs around processing time, automatic vs. manual decisions, approval rates,... can be defined in Optimize.
