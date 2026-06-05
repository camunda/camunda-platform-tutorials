# Vehicle Eligibility Check

Written by: Leila Ramos, Operations Business Analyst
Last updated: June 2026

---

## What this process does

This process checks whether a customer's vehicle qualifies for our seasonal maintenance promotion. A customer submits their VIN, the process looks up vehicle details automatically, runs the eligibility rules, and routes the case to either an eligible or ineligible outcome.

The promotion offers a discounted maintenance package to qualifying customers. We defined eligibility based on vehicle age and type, because older commercial vehicles carry too much maintenance risk for the promotion economics to work.

## Stakeholders

| Role           | Name        | Responsibility                                     |
|----------------|-------------|----------------------------------------------------|
| Process owner  | Leila Ramos | Eligibility rules, outcomes, process requirements  |
| Marketing      | TBD         | Promotion terms, customer communication            |
| Engineering    | TBD         | System integration, connector configuration        |

## Process flow

1. Customer submits a VIN at process start.
2. The **NHTSA VIN Lookup** service task calls the NHTSA vehicle database and returns the vehicle year, make, model, and type.
3. The **Determine Eligibility** business rule task evaluates the vehicle against our eligibility rules and outputs a risk score and an eligible flag.
4. A gateway routes based on `eligible`: eligible vehicles go to the eligible end event, ineligible vehicles go to the ineligible end event.

## Eligibility rules

The eligibility decision table (`vehicle-eligibility.dmn`) uses two inputs from the VIN lookup: **vehicle year** and **vehicle type** (as returned by NHTSA, e.g. `PASSENGER CAR`, `MOTORCYCLE`, `MULTIPURPOSE PASSENGER VEHICLE (MPV)`, `TRUCK`, `BUS`).

| Vehicle year  | Vehicle type                                | Risk score | Eligible? |
|---------------|---------------------------------------------|------------|-----------|
| 2022 or newer | Passenger car, motorcycle, or MPV           | 5          | Yes       |
| 2022 or newer | Any other type (trucks, buses, etc.)        | 30         | Yes       |
| 2018 to 2021  | Passenger car, motorcycle, or MPV           | 15         | Yes       |
| 2018 to 2021  | Any other type                              | 40         | Yes       |
| Before 2018   | Passenger car, motorcycle, or MPV           | 25         | Yes       |
| Before 2018   | Any other type                              | 50         | **No**    |

Rules apply in order; first match wins. Only pre-2018 non-personal vehicles (trucks, buses, trailers, etc.) are ineligible. All passenger cars, motorcycles, and MPVs qualify regardless of age.

The DMN outputs two process variables: `riskScore` (integer) and `eligible` (boolean). The gateway condition is `=eligible`.

---

## Instructions for Claude Code

Hi Claude. The BPMN and DMN are in this same folder. The test harness is already set up in the `test/` folder. I need you to complete the test scenarios file so our CI pipeline can validate the process before we go live.

**What to do:**

1. Read the BPMN file in this folder (`Vehicle Eligibility Check.bpmn`). Find the element IDs for:
   - The process definition ID (the `id` attribute on the `<bpmn:process>` element)
   - The NHTSA VIN lookup service task
   - The Determine Eligibility business rule task
   - The task on the eligible path (between the gateway and the eligible end event), if one exists
   - The task on the ineligible path (between the gateway and the ineligible end event), if one exists
   - The eligible end event
   - The ineligible end event

2. Open `test/src/test/resources/scenarios/vehicle-eligibility-check.test.json` and replace every `REPLACE_*` placeholder with the correct element ID from the BPMN. If there is no task between the gateway and an end event on a given path, remove the `COMPLETE_JOB` instruction for that path rather than leaving a placeholder.

3. Do not change test names, mock responses, or assertion logic — only the element IDs.

Use the eligibility rules table above if you need to confirm which scenario covers which DMN outcome.
