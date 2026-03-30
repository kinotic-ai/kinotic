# kinotic-auth

ABAC (Attribute-Based Access Control) policy expression language, parser, and compilers for the Kinotic platform.

---

## Policy Expression Language

Policies are plain-text strings that describe the conditions under which an action is permitted.

```
participant.role contains 'finance' and order.amount < 50000
entity.status in ['active', 'pending'] or participant.department == entity.department
not entity.deleted == true
entity.email like '*@kinotic.ai'
entity.approvedBy exists
```

---

### Paths

A path is a dotted sequence of identifiers that navigates to an attribute value.

```
participant.department
entity.address.city
context.time
order.amount
```

The first segment (the **root**) determines the source object:

| Root | Source |
|---|---|
| `participant` | The authenticated participant making the request |
| `context` | The request environment (e.g., `context.time`) |
| Any other name | A method parameter (service methods) or the entity document (entity definitions) |

Paths may be arbitrarily deep: `entity.address.city.name`.

---

### Operators

#### Comparison operators

| Operator | Meaning | Example |
|---|---|---|
| `==` | Equal | `entity.status == 'active'` |
| `!=` | Not equal | `entity.status != 'archived'` |
| `<` | Less than | `order.amount < 50000` |
| `<=` | Less than or equal | `order.amount <= 50000` |
| `>` | Greater than | `entity.level > 3` |
| `>=` | Greater than or equal | `entity.level >= 3` |
| `in` | Value is one of a set | `entity.status in ['active', 'pending']` |
| `contains` | Collection contains a value | `participant.roles contains 'admin'` |
| `exists` | Attribute is present | `entity.approvedBy exists` |
| `like` | Wildcard string match (`*` matches any sequence) | `entity.email like '*@kinotic.ai'` |

**`in`** — the left path's value must be one of the literals in the array:
```
entity.region in ['us-east', 'us-west', 'eu-central']
```

**`contains`** — the left path (a collection or string field) must contain the literal value:
```
participant.roles contains 'finance-approver'
```

**`exists`** — the attribute must be present (non-null) on the document or object:
```
entity.reviewedAt exists
```

**`like`** — wildcard match where `*` matches any sequence of characters:
```
entity.email like '*@example.com'
entity.code like 'ORD-*'
```

#### Boolean operators

| Operator | Precedence | Meaning |
|---|---|---|
| `not` | Highest | Logical negation |
| `and` | Middle | Logical conjunction |
| `or` | Lowest | Logical disjunction |

`and` binds more tightly than `or`, matching standard boolean algebra:

```
a or b and c    →  a or (b and c)
```

Use parentheses to override:

```
(a or b) and c
```

---

### Literals

| Type | Syntax | Examples |
|---|---|---|
| String | Single-quoted | `'finance'`, `'active'` |
| Integer | Digits | `42`, `50000` |
| Decimal | Digits with dot | `3.14`, `0.5` |
| Boolean | `true` / `false` (case-insensitive) | `true`, `FALSE` |

Keywords (`and`, `or`, `not`, `in`, `contains`, `exists`, `like`, `true`, `false`) are **case-insensitive**.

---

### Examples

```
# Role check on a service method
participant.role contains 'finance'

# Amount limit for a specific role
participant.role contains 'finance' and order.amount < 50000

# Multi-tenant isolation: participant's tenant must match the entity's tenant
participant.tenantId == entity.tenantId

# Status filter for read queries
entity.status in ['active', 'pending']

# Domain-based access
entity.email like '*@kinotic.ai'

# Require approval field to be set
entity.approvedBy exists

# Combined: active entities in the participant's department
entity.status == 'active' and entity.department == participant.department

# Override precedence with parentheses
(entity.status == 'pending' or entity.status == 'review') and participant.level >= 2
```

---

## Compilation Targets

The AST produced by parsing a policy string can be compiled to multiple evaluation targets.

### Cedar (`CedarCompiler`)

Compiles to a Cedar `when` clause body for allow/deny evaluation:

```java
PolicyExpression expr = PolicyExpressionParser.parse(
    "participant.role contains 'finance' and order.amount < 50000");
String cedarCondition = CedarCompiler.compile(expr);
// → (principal.role.contains("finance") && resource.amount < 50000)
```

Path mapping:

| Policy path | Cedar path |
|---|---|
| `participant.*` | `principal.*` |
| `context.*` | `context.*` |
| Everything else | `resource.*` |

### Elasticsearch (`EsQueryCompiler`)

Compiles to an Elasticsearch `Query` for injecting authorization filters into read queries. Participant attribute paths are resolved to concrete values at compile time using a provided attributes map:

```java
Map<String, Object> participantAttrs = Map.of("department", "finance");
PolicyExpression expr = PolicyExpressionParser.parse(
    "participant.department == entity.department and entity.status in ['active', 'pending']");
Query filter = EsQueryCompiler.compile(expr, participantAttrs);
// → bool filter: [ term(department, "finance"), terms(status, ["active", "pending"]) ]
```

**Rule:** The left-hand side of every comparison in an ES-compiled expression must be an entity/document path. `participant.*` and `context.*` paths must appear on the right-hand side so they can be resolved to concrete values from the `participantAttributes` map.

---

## Usage

### On published service methods — `@AbacPolicy`

```java
@Publish
public interface OrderService {

    @AbacPolicy("participant.role contains 'finance' and order.amount < 50000")
    Mono<Order> placeOrder(Order order, Participant participant);

    // Multiple policies are ANDed together
    @AbacPolicy("participant.tenantId == order.tenantId")
    @AbacPolicy("participant.role contains 'manager'")
    Mono<Void> approveOrder(Order order, Participant participant);
}
```

Identifiers in the expression resolve against method parameter names. `participant` always refers to the authenticated `Participant` (injected from the `SENDER_HEADER`); all other roots match parameter names by position.

### On entity definitions — `AbacPolicyDecorator`

```java
new AbacPolicyDecorator()
    .setExpression("participant.department == entity.department and entity.status in ['active', 'pending']")
```

In entity definition context, `entity` refers to the document being operated on and `participant` refers to the authenticated participant. The expression is compiled to an ES filter and injected into read queries so unauthorized documents are never returned.
