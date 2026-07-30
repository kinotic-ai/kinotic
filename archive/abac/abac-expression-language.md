---
title: ABAC Expression Language
description: Complete reference for the ABAC policy expression language.
navigation:
  icon: i-lucide-file-code
---

## Overview

The ABAC (Attribute-Based Access Control) expression language is used to write policy expressions for both service method authorization and entity data filtering. The same syntax works in both `@AbacPolicy` (service methods) and `$AbacPolicy` (entity decorators).

## Attribute Paths

Expressions reference attributes using dotted notation. The root identifier determines what the path resolves against.

### Reserved Roots

| Root | Description |
|------|-------------|
| `participant` | The authenticated caller. Access roles, department, limits, and other identity attributes. |
| `context` | The request environment. Access time, IP address, and other contextual data. |

### Dynamic Roots

Any root identifier that is not `participant` or `context` is resolved based on the policy context:

- **Service method policies** — Resolved against method parameter names. For `placeOrder(order: Order)`, the root `order` refers to the first argument.
- **Entity policies** — The root `entity` refers to the entity being accessed.

### Examples

```
participant.roles                  -- caller's roles array
participant.department             -- caller's department
participant.transferLimit          -- caller's transfer limit
context.time                       -- request timestamp
entity.ownerId                     -- entity's ownerId field
entity.sharedWith                  -- entity's sharedWith array
order.amount                       -- method parameter's amount field
transfer.fromAccount               -- method parameter's fromAccount field
```

## Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `==` | Equals | `participant.department == 'engineering'` |
| `!=` | Not equals | `entity.status != 'archived'` |
| `<` | Less than | `order.amount < 50000` |
| `>` | Greater than | `entity.priority > 3` |
| `<=` | Less than or equal | `transfer.amount <= participant.transferLimit` |
| `>=` | Greater than or equal | `entity.score >= 80` |
| `contains` | Collection membership | `participant.roles contains 'admin'` |
| `in` | Value in set | `entity.status in ['active', 'pending']` |
| `exists` | Field presence | `entity.approvedBy exists` |
| `like` | Pattern match (wildcards) | `entity.email like '*@kinotic.ai'` |

### Operator Details

**`contains`** — Tests whether a collection (array) includes a value. The left side must be a collection attribute.

**`in`** — Tests whether a value is present in a literal set. The right side must be a bracketed list of literals.

**`exists`** — Tests whether a field is present and non-null. No right-hand value is needed.

**`like`** — Pattern matching with `*` as a wildcard. `*@kinotic.ai` matches any string ending with `@kinotic.ai`.

## Boolean Logic

Combine conditions with `and`, `or`, and `not`. Parentheses override default precedence. All keywords are case-insensitive.

### Precedence

From highest to lowest:

1. Comparisons (`==`, `!=`, `<`, `>`, `<=`, `>=`, `contains`, `in`, `exists`, `like`)
2. `not`
3. `and`
4. `or`

### Examples

```
-- AND: both conditions must be true
participant.roles contains 'finance' and order.amount < 50000

-- OR: either condition must be true
entity.status in ['active', 'pending'] or participant.department == entity.department

-- NOT: negates the condition
not entity.deleted == true

-- Parentheses: override precedence
(participant.roles contains 'admin' or participant.roles contains 'manager')
    and entity.classification != 'top-secret'
```

## Literals

| Type | Syntax | Example |
|------|--------|---------|
| String | Single quotes | `'finance'` |
| Integer | Digits | `50000` |
| Decimal | Digits with dot | `3.14` |
| Boolean | `true` / `false` | `true` |
| Set | Brackets with literals | `['active', 'pending']` |

## Common Patterns

### Role Checks

```
-- Single role
participant.roles contains 'admin'

-- Any of multiple roles (OR)
participant.roles contains 'admin' or participant.roles contains 'manager'

-- All of multiple roles (AND)
participant.roles contains 'finance' and participant.roles contains 'approver'
```

### Ownership

```
-- Entity owner
entity.ownerId == participant.id

-- Owner or shared
entity.ownerId == participant.id or entity.sharedWith contains participant.id
```

### Department-Based

```
-- Same department
entity.department == participant.department

-- Specific department
participant.department == 'engineering'
```

### Amount Limits

```
-- Under a fixed limit
order.amount < 50000

-- Under caller's personal limit
transfer.amount <= participant.transferLimit
```

### Status Filtering

```
-- Specific statuses
entity.status in ['active', 'pending']

-- Exclude archived
entity.status != 'archived'

-- Exclude soft-deleted
not entity.deleted == true
```

### Combined Policies

```
-- Finance role with amount cap
participant.roles contains 'finance' and order.amount < 50000

-- Admin or owner
participant.roles contains 'admin' or entity.ownerId == participant.id

-- Department match with classification restriction
participant.department == entity.department
    and entity.classification != 'top-secret'
```
