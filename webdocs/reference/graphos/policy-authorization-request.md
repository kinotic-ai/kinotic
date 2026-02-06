# PolicyAuthorizationRequest

The `PolicyAuthorizationRequest` interface is a core part of the Structures policy system. It represents an individual policy request passed to the `PolicyAuthorizer` for evaluation. Each `PolicyAuthorizationRequest` encapsulates the details of a single policy, providing methods to mark it as authorized or denied.

## Interface Overview

```java
public interface PolicyAuthorizationRequest {

    /**
     * Retrieves the name of the policy being evaluated.
     *
     * @return The policy name as a String.
     */
    String policy();

    /**
     * Marks the policy as authorized, indicating that it passed the evaluation.
     */
    void authorize();

    /**
     * Marks the policy as denied, indicating that it failed the evaluation.
     */
    void deny();

    /**
     * Checks if the policy has been authorized.
     *
     * @return True if the policy is authorized, otherwise false.
     */
    boolean isAuthorized();
}
```

## Usage

The `PolicyAuthorizationRequest` is used during the policy evaluation process by the `PolicyAuthorizer`. Each policy in the evaluation set is represented by a `PolicyAuthorizationRequest`, and the `PolicyAuthorizer` interacts with it to determine whether the policy passes or fails.

## Example Workflow

1. A `PolicyAuthorizationRequest` is created for each unique policy in a request.
2. The `PolicyAuthorizer` processes the requests and calls either `authorize()` or `deny()` based on the evaluation result.
3. The `isAuthorized()` method can be used to query the evaluation status of the policy at runtime.

## Integration with PolicyAuthorizer

The `PolicyAuthorizationRequest` is passed as part of a list to the `PolicyAuthorizer` for batch evaluation. Each request's evaluation outcome directly impacts whether the associated operation, entity, or field is authorized.

For more details, see [PolicyAuthorizer](./policy-authorizer).

## Related Links

- Explore the [PolicyAuthorizer](./policy-authorizer) and its role in evaluating policy requests.
- Learn how to define and use [Policy Decorators](../../guide/graphos/policy-decorators) in your Structures models.
- Review the [Policy Evaluation Flow](../../guide/graphos/policy-evaluation-flow) for a complete understanding of runtime policy processing.
