# Trade Exception Remediation Processing

**Technical level:** Medium
<br>
**Industry:** Financial services
<br>
**Use Cases:** Human Task Orchestration

## Summary

This process blueprint depicts the end-to-end lifecycle of limit orders. The execution of the order is not in scope and handled via an asynchronous interface. The limit order is validated via business rules in form of a DMN table. This table can be easily extended with additional rules and use cases.

If the limit order is rejected, the broker is informed. The broker analyst contacts the customer to remediate the order. There are two possible outcomes: the order is updated or cancelled. If the limit order is accepted, the order is registered with the stock exchange. Due to the nature of limit orders, the order can be either executed or it expires. In both, the successful and unsuccessful case, the customer is informed about the outcome.

This blueprint contains various events that represent certain milestones in the process flow. These events can be used in Optimize to gain insights into the duration of certain steps, the ratio of first-yield passes, etc.
