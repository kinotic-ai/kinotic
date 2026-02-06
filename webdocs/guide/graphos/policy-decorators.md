# Policy Decorators

Policy decorators in Structures allow you to define fine-grained access control directly within your TypeScript models. These decorators are used to specify policies for operations, entities, and fields, enabling seamless integration with GraphOS and consistent enforcement of access control.

## Key Decorators

### `@Policy`

The `@Policy` decorator applies policies to entities and fields. It defines the conditions under which the associated entity or field can be accessed.

- **Entity-Level Policies**: Applied to an entity to define access requirements for the entire model.
```typescript
@Policy([['data:read']])
@Entity(MultiTenancyType.SHARED)
export class Person {
  public firstName!: string;
  public lastName!: string;
}
```

- **Field-Level Policies**: Applied to individual fields to restrict access based on specific policies.
```typescript
@Entity(MultiTenancyType.SHARED)
export class Person {
    public firstName!: string;
    public lastName!: string;
    @Policy([['data:create']])
    public address!: Address;
}
```

### [`@EntityServiceDecorators`](./entity-service-decorators)

The [`@EntityServiceDecorators`](./entity-service-decorators) decorator allows you to define policies for specific operations at the entity level. These include CRUD operations such as `create`, `read`, `update`, and `delete`.

- **Operation-Level Policies**: Define access requirements for operations like `create` and `update`.
```typescript
@EntityServiceDecorators(
    {
        allCreate: [
            $Policy([['data:create']]),
        ],
        findAll: [
            $Policy([['data:read']])
        ]
    }
)
export class Person { ... }
```
::: tip
More information on the `@EntityServiceDecorators` decorator can be found [here](./entity-service-decorators).
:::

## How Policies Translate to GraphQL

Policies defined with these decorators are automatically translated into GraphOS-compatible `@policy` directives in the GraphQL schema.

- The policies applied at the entity and field levels in Structures directly map to corresponding `@policy` directives in GraphQL.

This ensures that policies are enforced consistently across the generated GraphQL API.

## Benefits

- **Centralized Access Control**: Policies are defined directly in the model, ensuring a single source of truth for access control.
- **Granular Control**: Apply policies at the operation, entity, and field levels to match your exact requirements.
- **GraphOS Compatibility**: Policies map directly to GraphQL schema directives for use with GraphOS and the Apollo Router.

## Related Links

- Learn about the [PolicyAuthorizationService](../../reference/graphos/policy-authorization-service) and its role in enforcing policies.
- Explore the [Policy Evaluation Flow](./policy-evaluation-flow) to understand runtime policy processing.
