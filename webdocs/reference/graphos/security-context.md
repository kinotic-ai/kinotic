# SecurityContext

The `SecurityContext` provides contextual information about the user or system making a request. It is a critical component in the policy evaluation flow, enabling the `PolicyAuthorizer` to evaluate policies based on the user's identity, roles, permissions, or other security attributes.

## Purpose

- **Authorization Context**: Supplies data required to determine whether a policy should be authorized or denied.
- **Flexibility**: Allows integration with custom security frameworks or identity providers.
- **Consistency**: Ensures that authorization decisions are based on a standard structure for user and request data.

## Key Responsibilities

1. **Identity Information**: Provides details about the authenticated user or system identity.
2. **Role and Permission Management**: Includes roles, permissions, or claims associated with the user.
3. **Dynamic Attributes**: Supports additional metadata required for contextual policy evaluation, such as request time or IP address.

## Integration with Policy Evaluation

The `SecurityContext` is passed to the `PolicyAuthorizer` during policy evaluation. The `PolicyAuthorizer` uses this context to:

1. Validate whether the user meets the requirements specified in the `PolicyAuthorizationRequest`.
2. Apply dynamic checks based on user attributes or environmental variables.
3. Enforce policies consistently across operations, entities, and fields.

*** Example Here: Passing SecurityContext to PolicyAuthorizer ***

## Example Use Cases

1. **Role-Based Authorization**:
    - Policies requiring a specific role (e.g., "admin") are checked against the roles in the `SecurityContext`.

2. **Attribute-Based Authorization**:
    - Policies that depend on request parameters, such as time of access, can be validated using additional attributes in the context.

3. **Tenant-Specific Policies**:
    - Multi-tenant applications can enforce policies based on tenant ID stored in the `SecurityContext`.

*** Example Here: Attribute-Based Authorization with SecurityContext ***

## Benefits

- **Standardized Input**: Provides a unified format for passing security data into the policy evaluation process.
- **Flexible Extensions**: Can be extended to include custom attributes required for complex authorization logic.
- **Reusable Design**: Works with various authentication mechanisms, making it adaptable to different environments.

## Next Steps

- Learn how the [PolicyAuthorizer](./policy-authorizer) uses the `SecurityContext` during policy evaluation.
- Explore the [PolicyAuthorizationService](./policy-authorization-service) for runtime enforcement of policies.
- Review the [Policy Evaluation Flow](../../guide/graphos/policy-evaluation-flow) to understand the full process of policy validation.
