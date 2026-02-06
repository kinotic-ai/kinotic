# PolicyAuthorizer

The `PolicyAuthorizer` interface is a critical component in the Structures policy system, responsible for authorizing a list of policy requests. It provides the mechanism to evaluate whether specific policies are allowed or denied based on the provided `SecurityContext`.
## Interface Overview

The `PolicyAuthorizer` defines a single method:

```java
public interface PolicyAuthorizer {

    /**
     * Authorizes a list of {@link PolicyAuthorizationRequest}s based on the provided security context.
     *
     * @param requests        A list of {@link PolicyAuthorizationRequest}s to evaluate.
     * @param securityContext The security context containing authentication and authorization details.
     * @return A {@link CompletableFuture} that completes successfully if all policies are authorized,
     *         or completes exceptionally if any policy is denied.
     */
    CompletableFuture<Void> authorize(List<PolicyAuthorizationRequest> requests, SecurityContext securityContext);

}
```

## Parameters

- **`requests`**: A list of `PolicyAuthorizationRequest` objects, each representing a specific policy to be authorized.
- **`securityContext`**: Contains information about the user or system making the request, such as roles, permissions, or authentication details.

[//]: # (TODO: Make this correct for the behavior we actually have. I may not be a bad idea to let the user fail the whole requeest this way..)
## Return Value

- **Returns a `CompletableFuture<Void>`**:
    - **Successful Completion**: All policies are authorized.
    - **Exceptional Completion**: One or more policies are denied, resulting in an `AuthorizationException`.

## Usage in Structures

The `PolicyAuthorizationService` relies on the `PolicyAuthorizer` to:

1. Verify the authorization for each policy using the `authorize` method.
2. Enforce the results of the policy evaluation during runtime.

## Related Classes

### PolicyAuthorizationRequest

The `PolicyAuthorizationRequest` represents individual policy requests passed to the `authorize` method. Each request includes:

- The policy name.
- Methods to mark the policy as authorized or denied.

For more details, see [PolicyAuthorizationRequest](./policy-authorization-request).

### SecurityContext

The `SecurityContext` encapsulates the user’s authentication and authorization information. It provides the context needed to evaluate the policies.

For more details, see [SecurityContext](./security-context).

## Example Implementations

The `PolicyAuthorizer` can be implemented in various ways, depending on the requirements:

- **Mock Authorizer**: For testing, a simple implementation can mock authorization behavior.
- **Role-Based Authorizer**: Uses roles and permissions to determine authorization.
- **External Service Integration**: Calls an external system to evaluate policies dynamically.

## Benefits

- **Flexible Design**: Customizable to fit different authorization strategies.
- **Asynchronous Processing**: Leverages `CompletableFuture` for non-blocking authorization logic.
- **Integration Ready**: Works seamlessly with Structures’ policy system and external services.

## Next Steps

- Understand how [PolicyAuthorizationService](./policy-authorization-service) uses `PolicyAuthorizer` for runtime policy enforcement.
- Explore the [PolicyAuthorizationRequest](./policy-authorization-request) and its role in defining individual policy evaluations.
- Learn about [Policy Decorators](../../guide/graphos/policy-decorators) and how they define policies in Structures models.
