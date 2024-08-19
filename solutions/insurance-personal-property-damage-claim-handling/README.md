# Insurance: Personal Property Damage Claim Handling

**Technical level:** Low
<br>
**Industry:** Insurance
<br>
**Use Cases:** Claim Handling/Human Task Orchestration

## Summary

This blueprint describes a simple claim handling process. It shows the interaction between the customer, the insurance's IT systems and also a Gen AI tool to analyze the photo of the damaged property. In this process, the claim is either approved automatically, determined by various business rules specified in a DMN table, or adjudicated manually.

Intermediate none events are used to keep track of the different outcomes and milestones: the claim is either approved automatically, approved manually, or declined manually. These events are suitable to define KPIs in Optimize, which in turn can help improve the rules for automatic claim processing to minimize costs.
