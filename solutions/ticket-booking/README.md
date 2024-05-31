# Ticket Booking
**Technical level:** Medium 
<br>
**Industry:** Financial Services
Media & Entertainment 
<br>
**Use Cases:** API Orchestration

## Summary

This process handles ticket sales for events. In the first step, we reserve the respective seats. Once the seats have been reserved, we request that the payment will be retrieved and then wait for an event that confirms the arrival of the payment. If that event does not arrive within 5 minutes, the booking is canceled. Once we've received the payment, we trigger the ticket generation in the respective system.