# Bank: Customer Complaint/Dispute Handling

**Technical level:** Low
<br>
**Industry:** Financial services
<br>
**Use Cases:** Claim Handling/Human Task Orchestration

## Summary

In this blueprint, a credit card dispute process is depicted. First, the customer is requested to provide documents to proof their credit card fraud claim. The "Document Request Process" can be easily adapted in terms of how to contact the customer (outbound connectors), how often to notify the customer, when to abort the process, and how to orchestrate the customer's reponse.

Next, the "Fraud Claim Investigation" is started. In "Vendor fraud claim validation", the bank requests documentation from the vendor. This process can also be adpated in terms of orchestrating the communcation, and when to continue and not wait further for the vendor's response. A business rule task is used to decide between 4 different scenarios. To proceed automatically, or not, and to refund or not, with all 4 scenarios being possible. In the case of a manual decision, the fraud rating is presented to the fraud analyst.
