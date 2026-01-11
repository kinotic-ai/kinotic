# EntityServiceDecorators

The `@EntityServiceDecorators` decorator in Structures enables fine-grained access control by allowing you to define policies for operations at the entity level. This includes both general CRUD operations (`create`, `read`, `update`, and `delete`) and specific operations such as `bulkSave` or `findById`.

## How It Works

### General CRUD Policies

- **General CRUD Policies**: The decorator supports defining general policies for CRUD operations. These are specified using fields like `allCreate`, `allRead`, `allUpdate`, and `allDelete`, which apply to all operations associated with these actions.
    - For example, `allCreate` applies to operations like `save` and `bulkSave`.

### Specific Operation Policies

- **Specific Operation Policies**: In addition to general CRUD policies, the decorator allows defining policies for specific operations, such as `bulkSave` or `countByQuery`. These policies take precedence over the general CRUD policies for their respective operations.

### Precedence of Policies

When determining which policies to apply for an operation, the system resolves the correct set of policies:
1. **Specific Operation Policies**: If policies are defined for a specific operation (e.g., `bulkSave`), these are used.
2. **General CRUD Policies**: If no specific operation policies are defined, the corresponding CRUD policies (e.g., `allCreate` for `bulkSave`) are applied.

This precedence ensures that specific operation requirements can override the more general CRUD-based policies, providing greater flexibility and control.

### Policy Resolution Example

1. For a `bulkSave` operation:
    - If specific policies are defined in `bulkSave`, they are applied.
    - If not, the policies in `allCreate` are used.

2. For a `findById` operation:
    - If specific policies are defined in `findById`, they are applied.
    - If not, the policies in `allRead` are used.

### Benefits of Specific Operation Policies

- **Granular Control**: Tailor policies to specific operations without affecting the broader CRUD operation policies.
- **Clear Precedence Rules**: Ensures that specific needs for individual operations are respected while maintaining general CRUD policies as a fallback.

## Example Usage

1. **Define General CRUD Policies**: Use fields like `allCreate` or `allRead` to specify policies for all `create` or `read` operations.
```typescript
import { Entity, EntityServiceDecorators, MultiTenancyType, $Policy } from '@mindignited/structures-api';

@EntityServiceDecorators({
    allCreate: [
        $Policy([['data:create']])
    ],
    allRead: [
        $Policy([['data:read']])
    ],
    allUpdate: [
        $Policy([['data:update']])
    ],
    allDelete: [
        $Policy([['data:delete']])
    ]
})
@Entity(MultiTenancyType.SHARED)
export class Person {
    public firstName!: string;
    public lastName!: string;
}
```

2. **Define Specific Operation Policies**: Add policies for individual operations like `bulkSave` or `findById` to override the general CRUD policies for these specific cases.
```typescript
import { Entity, EntityServiceDecorators, MultiTenancyType, $Policy } from '@mindignited/structures-api';

@EntityServiceDecorators({
    bulkSave: [
        $Policy([['data:bulkSave']])
    ],
    findById: [
        $Policy([['data:findById']])
    ]
})
@Entity(MultiTenancyType.SHARED)
export class Person {
    public firstName!: string;
    public lastName!: string;
}
```

3. **Combined General and Specific Policies**: You can combine general CRUD policies and specific operation policies
```typescript
import { Entity, EntityServiceDecorators, MultiTenancyType, $Policy } from '@mindignited/structures-api';

@EntityServiceDecorators({
    allRead: [
        $Policy([['data:read']])
    ],
    findById: [
        $Policy([['data:findById']])
    ]
})
@Entity(MultiTenancyType.SHARED)
export class Person {
    public firstName!: string;
    public lastName!: string;
}
```


## Related Links

- Learn how [PolicyAuthorizationService](../../reference/graphos/policy-authorization-service) evaluates these policies during runtime.
- Explore how to define and apply [Policy Decorators](./policy-decorators) in your models.
- Understand the [Policy Evaluation Flow](./policy-evaluation-flow) for detailed insights into how policies are processed.
