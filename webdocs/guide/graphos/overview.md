# Policy Integration Overview

The `PolicyAuthorizationService` in Structures provides seamless integration with GraphOS-style policies and the Apollo Router, enabling a 1-to-1 mapping between defined policies and GraphQL schema functionality. This integration ensures that your data management workflows in Structures align with the robust, declarative authorization capabilities of GraphOS.

## Key Features

- **GraphOS-Compatible Policies**: Structures' `@Policy` decorator maps directly to GraphOS's `@policy` directive, enabling the same fine-grained access control mechanisms.
- **Automatic Schema Enrichment**: Policy decorators defined in Structures are automatically included in the generated GraphQL schema, ensuring consistency between your TypeScript models and the resulting schema.
- **Seamless Integration with Apollo Router**: Policies propagate through the Apollo Router, leveraging GraphOS's `@policy` directive for distributed enforcement.

## How It Works

1. **Defining Policies in Structures**:
   - Policies are defined using the `@Policy` decorator, applied at the operation, entity, and field levels.
   - These decorators directly map to GraphQL schema directives, ensuring a consistent translation from your TypeScript models to GraphOS-compatible schemas.

2. **Generate the GraphQL Schema**:
   - Structures automatically translates the `@Policy` decorators into `@policy` directives in the GraphQL schema.

3. **Enforce Policies with Apollo Router**:
   - Deploy your schema with the Apollo Router to enforce these policies using the new GraphOS capabilities.

## Benefits

- **Unified Policy Management**: Define policies once in Structures and apply them consistently across GraphQL and REST APIs.
- **GraphQL-Native Authorization**: Take advantage of GraphOS-style policies directly within your Structures-based applications.
- **Distributed Enforcement**: Policies seamlessly integrate with the Apollo Router, enabling distributed enforcement across your federated services.

## Next Steps

- Explore how to define [@Policy](./policy-decorators) in your Structures models.
- Understand the [Policy Evaluation Flow](./policy-evaluation-flow) for runtime authorization.
