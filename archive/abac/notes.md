# ABAC / Authorization docs — archived

This folder consolidates all documentation for the **ABAC / RBAC authorization
engine** (attribute- and role-based access control, policy expressions, the
`@AbacPolicy` / `$AbacPolicy` / `$Policy` / `$Role` decorators, and the "Cedar"
policy engine).

**None of this is implemented yet.** The docs described it as a working feature,
which was causing confusion, so the dedicated pages were moved here and every
reference to it was removed from the live docs, website, and README.

When the authorization engine is actually built, use this folder as the source
of truth for restoring the documentation.

## Moved documents

| Original location | Now at |
|---|---|
| `website/content/01.apps/06.security/01.access-control.md` | `archive/abac/access-control.md` |
| `website/content/01.apps/09.reference/03.abac-expression-language.md` | `archive/abac/abac-expression-language.md` |

### Related change

- `website/content/01.apps/06.security/02.authentication.md` was renumbered to
  `01.authentication.md` (it is now the only page in the Security section after
  `access-control.md` was moved out).

## References removed from live files

Everything below was deleted from the files listed. It is captured verbatim so
it can be restored when the engine ships.

### `website/content/02.platform/01.architecture.md`

- Core Components — RPC Gateway bullet, removed trailing sentence:
  > Enforces ABAC policies at the gateway layer before calls reach service implementations.
- Core Components — Persistence Layer bullet, removed trailing sentence:
  > Compiles ABAC policies into query filters so unauthorized data is never returned.
- Core Components — Auth System bullet, changed
  `Handles authentication (email/password and OIDC) and authorization (Cedar policy engine).`
  to `Handles authentication (email/password and OIDC).`
- Tech Stack table — removed row:
  > | Policy Engine | Cedar |

### `website/content/02.platform/09.contributing.md`

- Repository Structure table — `kinotic-core/` description, removed `, Cedar policy engine`:
  > RPC gateway, service registry, authentication, Cedar policy engine

### `website/content/02.platform/05.system-security.md`

- "What This Design Does Not Cover" — Authorization bullet, changed
  `Roles, policies, RBAC, and ABAC policy engine integration.`
  to `Roles and policies.` (the "future work populates it from the policy
  system" framing was kept).

### `website/content/01.apps/01.introduction.md`

- "Why Kinotic?" table — Auth row, removed `RBAC,`:
  > Wire up OIDC, RBAC, and session management yourself
- "Enterprise-ready" callout, removed `Role-based access control,`:
  > **Enterprise-ready.** Role-based access control, software bill of materials, and full observability mean you never have to rewrite for production.

### `website/content/01.apps/05.persistence/01.overview.md`

- Key Features — removed bullet:
  > - **Attribute-based access control (ABAC)** - Fine-grained data access policies applied at the persistence layer

### `website/content/01.apps/04.services/01.overview.md`

- Removed the entire trailing `## Access Control` section:
  > ## Access Control
  >
  > You can attach `@AbacPolicy` expressions to service methods to enforce authorization before the method runs. See [Access Control](/apps/security/access-control) for details.

### `website/content/01.apps/04.services/02.publishing-services.md`

- Removed the `### @AbacPolicy(expression)` decorator section:

```
### @AbacPolicy(expression)

Enforces attribute-based access control **before** the method is invoked. The expression can reference properties of the caller (`participant`) and the method arguments. Multiple `@AbacPolicy` decorators on the same method are combined with AND semantics -- all policies must pass.

```typescript
import { Publish, AbacPolicy } from '@kinotic-ai/core'

@Publish('com.example')
class OrderService {
    @AbacPolicy("participant.roles contains 'finance' and order.amount < 50000")
    placeOrder(order: Order): void {
        // Only reached if caller has 'finance' role AND order under 50k
    }
}
```

If any policy expression evaluates to `false`, the platform rejects the call before it reaches the service.
```

### `website/content/01.apps/06.security/02.authentication.md` (now `01.authentication.md`)

- Removed the trailing `## Policy-Based Authorization` section:

```
## Policy-Based Authorization

Once authenticated, authorization is handled by the platform. Policies are applied declaratively using decorators on your services and entities — no authorization logic in your application code.

The authenticated user's identity (email, roles, metadata) is available in ABAC policy expressions through the `participant` attribute path.

See [Access Control](/apps/security/access-control) for details on writing ABAC policies.
```

### `website/content/01.apps/09.reference/01.decorators.md`

- Removed the `### @EntityServiceDecorators(config)` entry (its only purpose is
  attaching `$AbacPolicy` / `$Policy` / `$Role` decorators to CRUD operations):

```
### `@EntityServiceDecorators(config)`

Configures per-operation decorators for the entity service. Used to apply ABAC policies, role checks, or other decorators to specific CRUD operations.

```typescript
import { Entity, EntityServiceDecorators, $AbacPolicy } from '@kinotic-ai/persistence'

@EntityServiceDecorators({
    allRead: [
        $AbacPolicy("entity.ownerId == participant.id")
    ],
    allDelete: [
        $AbacPolicy("participant.roles contains 'admin'")
    ]
})
@Entity()
export class Document {
    // ...
}
```

**Operation groups:** `allCreate`, `allRead`, `allUpdate`, `allDelete`

**Individual operations:** `save`, `bulkSave`, `findById`, `findByIds`, `findAll`, `search`, `count`, `countByQuery`, `update`, `bulkUpdate`, `deleteById`, `deleteByQuery`
```

- Removed the entire `## Entity Service Policy Decorators` section:

```
## Entity Service Policy Decorators

These factory functions create decorator instances for use within `@EntityServiceDecorators`. They are imported from `@kinotic-ai/persistence`.

### `$AbacPolicy(expression)`

Creates an ABAC policy decorator for entity operations. The expression uses the [ABAC expression language](/apps/reference/abac-expression-language).

```typescript
import { $AbacPolicy } from '@kinotic-ai/persistence'

$AbacPolicy("entity.ownerId == participant.id")
$AbacPolicy("participant.roles contains 'admin'")
```

### `$Policy(policies)`

Creates a policy decorator with a matrix of policy rules.

```typescript
import { $Policy } from '@kinotic-ai/persistence'

$Policy([['admin', 'editor'], ['manager']])
```

### `$Role(roles)`

Creates a role-based access decorator for entity operations.

```typescript
import { $Role } from '@kinotic-ai/persistence'

$Role(['admin', 'editor'])
```
```

- Removed the `### @AbacPolicy(expression)` service-decorator entry:

```
### `@AbacPolicy(expression)`

Enforces an ABAC policy on a published service method. The policy is evaluated at the gateway before the method is invoked.

```typescript
import { Publish, AbacPolicy } from '@kinotic-ai/core'

@Publish('com.example')
class AdminService {
    @AbacPolicy("participant.roles contains 'admin'")
    async deleteAllData(): Promise<void> {
        // Only reachable by callers with the 'admin' role
    }
}
```

See [Access Control](/apps/security/access-control) for detailed policy documentation.
```

### `README.md`

- "The Vision" — Enterprise Ready bullet, removed `RBAC,`:
  > * **Enterprise Ready:** Built-in RBAC, SBOM support, and observability from day one.
- "Service Directory" feature — removed bullet:
  > * RBAC policies required for access.

## Left in place on purpose

`website/app/components/home/FeaturesComponent.vue` — the "Security built in"
card still markets the unbuilt access-control engine ("Define who can do what
in plain language and the platform enforces it everywhere"). The homepage was
intentionally left unchanged; revisit this card when the engine ships.

`docs/future-prompts/Gateway ABAC.md` — the design prompt for the planned
authorization overhaul — was kept in the roadmap folder. It is a
forward-looking planning doc, not user-facing documentation presenting ABAC as
shipped.

`docs/future-prompts/Multi-environment architecture.md` still references the
planned Gateway ABAC work (and "RBAC-defined path patterns"). Those are
forward-looking references inside a design/planning doc — they describe future
work rather than presenting ABAC as shipped — so they were kept.

Kubernetes RBAC (`deployment/helm/**`, `deployment/terraform/**`,
`archive/docs/kubernetes/**`) is unrelated infrastructure role-binding, not the
application authorization engine, and was left untouched.
