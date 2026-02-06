# PolicyAuthorizationService

The `PolicyAuthorizationService` is the central class in Structures for managing and enforcing policies at runtime. It integrates GraphOS-style policy management with Structures' data modeling capabilities, ensuring that policies defined in your models are enforced seamlessly across operations, entities, and fields.

## Key Responsibilities

1. **Policy Enforcement**: Ensures that only authorized operations, entities, and fields can be accessed.
2. **Operation-Level Policies**: Evaluates policies applied to specific operations, such as `SAVE`, `READ`, or `DELETE`.
3. **Entity-Level Policies**: Determines whether access to an entire entity (structure) is allowed.
4. **Field-Level Policies**: Checks individual field access based on the policies defined in the model.

## How It Works

### Initialization

The `PolicyAuthorizationService` is initialized with:
- A `Structure` object that defines the entity, its fields, and their respective policies.
- A `PolicyAuthorizer` implementation responsible for evaluating individual policy requests.

During initialization:
- **Entity-Level Policies**: Extracted from the `PolicyDecorator` applied to the entity.
- **Field-Level Policies**: Extracted from the `PolicyDecorator` applied to individual fields.
- **Operation-Level Policies**: Extracted from the `EntityServiceDecoratorsConfig` mapped to specific `EntityOperation` values.

### Runtime Authorization

The `authorize` method evaluates policies for a requested operation:
1. **Operation Evaluation**: Checks if the requested operation is allowed based on its policies.
2. **Entity Evaluation**: Ensures access to the entire entity is permitted.
3. **Field Evaluation**: Verifies each field’s policies, accumulating any denied fields.
4. **Outcome**:
    - If all evaluations succeed, the operation proceeds.
    - If any evaluation fails, an appropriate `AuthorizationException` is thrown.

### Example Flow

1. A client requests to perform an operation (e.g., `READ`).
2. The `authorize` method maps the operation name to an `EntityOperation` and retrieves the corresponding evaluator.
3. The evaluator checks the policies sequentially:
    - Operation policies.
    - Entity policies.
    - Field policies.
4. If any policy fails, the request is denied with detailed feedback about the failure.

## Integration with GraphOS

The `PolicyAuthorizationService` directly supports the new GraphOS `@policy` directive by ensuring:
- **1-to-1 Mapping**: Policies defined in Structures are automatically translated to GraphOS-compatible schema directives.
- **Runtime Enforcement**: Policies are enforced at runtime, ensuring consistent behavior with your GraphQL API.

## Benefits

- **Centralized Management**: Unified handling of operation, entity, and field-level policies.
- **GraphOS Compatibility**: Seamless integration with GraphOS-style `@policy` directives for federated GraphQL APIs.
- **Granular Control**: Fine-grained access control for operations, entities, and fields.

## Related Links

- Learn about the [PolicyAuthorizer](./policy-authorizer) used for evaluating individual policies.
- Explore how to define [PolicyDecorators](../../guide/graphos/policy-decorators) in your Structures models.
- Understand the [Policy Evaluation Flow](../../guide/graphos/policy-evaluation-flow) for runtime authorization.
