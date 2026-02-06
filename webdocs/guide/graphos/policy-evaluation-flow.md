# Policy Evaluation Flow

The policy evaluation flow in Structures ensures that policies defined at the operation, entity, and field levels are correctly processed and enforced during runtime. This flow integrates seamlessly with the `PolicyAuthorizationService`, ensuring that only authorized actions are performed.

## Overview

The policy evaluation flow follows these key steps:

1. **Policy Collection**:
    - Collect all relevant policies from the operation, entity, and field levels.
    - Deduplicate the policies to create a unique set of `PolicyAuthorizationRequest` objects.

2. **Policy Evaluation**:
    - Pass the collected policies to the `PolicyAuthorizer` for evaluation.
    - The `PolicyAuthorizer` processes each `PolicyAuthorizationRequest`, marking it as either authorized or denied.

[//]: # (TODO: Make sure this section is correct for the behavior we actually want long term)
3. **Result Aggregation**:
    - Aggregate the evaluation results to determine:
        - If the operation is allowed.
        - If access to the entity is permitted.
        - Which fields (if any) are denied. 

4. **Outcome Determination**:
    - If the operation or entity-level policies fail, the entire request is denied.
    - If field-level policies fail, those fields are excluded from the result.

## Detailed Steps

### Step 1: Policy Collection
- The `PolicyEvaluator` gathers all relevant policies:
    - **Operation Policies**: Extracted from the operation being performed.
    - **Entity Policies**: Retrieved from the entity's `PolicyDecorator`.
    - **Field Policies**: Collected from the `PolicyDecorator` of each field in the entity.

### Step 2: Policy Evaluation
- The unique set of policies is passed to the `PolicyAuthorizer`.
- Each policy is evaluated in the context of the provided `SecurityContext`.
    - The `authorize()` or `deny()` methods are called on the `PolicyAuthorizationRequest` objects based on the evaluation.

### Step 3: Result Aggregation
- The results of the evaluation are aggregated:
    - **Operation-Level Policies**: Determines if the operation can proceed.
    - **Entity-Level Policies**: Ensures access to the entire entity is allowed.
    - **Field-Level Policies**: Identifies any fields that should be excluded from the response.

### Step 4: Outcome Determination
- If any operation or entity-level policy is denied:
    - The entire request is rejected with an `AuthorizationException`.
- If field-level policies fail:
    - The denied fields are excluded from the response, while the rest of the request proceeds.

## Integration with `PolicyAuthorizationService`

The `PolicyAuthorizationService` orchestrates the evaluation flow by:
- Mapping operation names to `EntityOperation` values.
- Using `PolicyEvaluator` to collect and evaluate policies.
- Determining the final outcome based on the evaluation results.

## Related Links

- Learn about the [PolicyAuthorizationService](../../reference/graphos/policy-authorization-service) and its role in orchestrating policy evaluation.
- Explore how to define and use [Policy Decorators](./policy-decorators) in your models.
- Review the [PolicyAuthorizer](../../reference/graphos/policy-authorizer) and its role in evaluating individual policies.
