# Car Rental: Booking Process

**Technical level:** Medium
<br>
**Industry:** Car Rental
<br>
**Use Cases:** Booking Process/Human Task Orchestration

## Summary

This blueprint depicts a car rental booking process with various features:

- validation of driver's license and potential credit score check depending on the car's value
- a car availability and readiness check with various potential outcomes
- a booking process with a potential invalid payment method
- various message and timer events to monitor the sequence of events
- an escalation process and automatic calculation of additional costs

Various events can be used to create KPI reports in Optimize, e.g.:

- the percentage of invalid driver's licenses
- the percentage of customers lost due to insufficient credit score
- first yield rate for car availability and readiness
- percentage of cars not returned on time, returned during or after escalation

The specific subprocesses can be easily adapted to fit your specific use case!
