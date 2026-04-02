# ABAC Expression Language

> Complete reference for the ABAC policy expression language.

## Overview

The ABAC (Attribute-Based Access Control) expression language is used to write policy expressions for both service method authorization and entity data filtering. The same syntax works in both `@AbacPolicy` (service methods) and `$AbacPolicy` (entity decorators).

## Attribute Paths

Expressions reference attributes using dotted notation. The root identifier determines what the path resolves against.

### Reserved Roots

<table>
<thead>
  <tr>
    <th>
      Root
    </th>
    
    <th>
      Description
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        participant
      </code>
    </td>
    
    <td>
      The authenticated caller. Access roles, department, limits, and other identity attributes.
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        context
      </code>
    </td>
    
    <td>
      The request environment. Access time, IP address, and other contextual data.
    </td>
  </tr>
</tbody>
</table>

### Dynamic Roots

Any root identifier that is not `participant` or `context` is resolved based on the policy context:

- **Service method policies** — Resolved against method parameter names. For `placeOrder(order: Order)`, the root `order` refers to the first argument.
- **Entity policies** — The root `entity` refers to the entity being accessed.

### Examples

```text
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

<table>
<thead>
  <tr>
    <th>
      Operator
    </th>
    
    <th>
      Description
    </th>
    
    <th>
      Example
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        ==
      </code>
    </td>
    
    <td>
      Equals
    </td>
    
    <td>
      <code>
        participant.department == 'engineering'
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        !=
      </code>
    </td>
    
    <td>
      Not equals
    </td>
    
    <td>
      <code>
        entity.status != 'archived'
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        <
      </code>
    </td>
    
    <td>
      Less than
    </td>
    
    <td>
      <code>
        order.amount < 50000
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        >
      </code>
    </td>
    
    <td>
      Greater than
    </td>
    
    <td>
      <code>
        entity.priority > 3
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        <=
      </code>
    </td>
    
    <td>
      Less than or equal
    </td>
    
    <td>
      <code>
        transfer.amount <= participant.transferLimit
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        >=
      </code>
    </td>
    
    <td>
      Greater than or equal
    </td>
    
    <td>
      <code>
        entity.score >= 80
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        contains
      </code>
    </td>
    
    <td>
      Collection membership
    </td>
    
    <td>
      <code>
        participant.roles contains 'admin'
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        in
      </code>
    </td>
    
    <td>
      Value in set
    </td>
    
    <td>
      <code>
        entity.status in ['active', 'pending']
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        exists
      </code>
    </td>
    
    <td>
      Field presence
    </td>
    
    <td>
      <code>
        entity.approvedBy exists
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        like
      </code>
    </td>
    
    <td>
      Pattern match (wildcards)
    </td>
    
    <td>
      <code>
        entity.email like '*@kinotic.ai'
      </code>
    </td>
  </tr>
</tbody>
</table>

### Operator Details

**contains** — Tests whether a collection (array) includes a value. The left side must be a collection attribute.

**in** — Tests whether a value is present in a literal set. The right side must be a bracketed list of literals.

**exists** — Tests whether a field is present and non-null. No right-hand value is needed.

**like** — Pattern matching with `*` as a wildcard. `*@kinotic.ai` matches any string ending with `@kinotic.ai`.

## Boolean Logic

Combine conditions with `and`, `or`, and `not`. Parentheses override default precedence. All keywords are case-insensitive.

### Precedence

From highest to lowest:

1. Comparisons (`==`, `!=`, `<`, `>`, `<=`, `>=`, `contains`, `in`, `exists`, `like`)
2. `not`
3. `and`
4. `or`

### Examples

```text
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

<table>
<thead>
  <tr>
    <th>
      Type
    </th>
    
    <th>
      Syntax
    </th>
    
    <th>
      Example
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      String
    </td>
    
    <td>
      Single quotes
    </td>
    
    <td>
      <code>
        'finance'
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      Integer
    </td>
    
    <td>
      Digits
    </td>
    
    <td>
      <code>
        50000
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      Decimal
    </td>
    
    <td>
      Digits with dot
    </td>
    
    <td>
      <code>
        3.14
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      Boolean
    </td>
    
    <td>
      <code>
        true
      </code>
      
       / <code>
        false
      </code>
    </td>
    
    <td>
      <code>
        true
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      Set
    </td>
    
    <td>
      Brackets with literals
    </td>
    
    <td>
      <code>
        ['active', 'pending']
      </code>
    </td>
  </tr>
</tbody>
</table>

## Common Patterns

### Role Checks

```text
-- Single role
participant.roles contains 'admin'

-- Any of multiple roles (OR)
participant.roles contains 'admin' or participant.roles contains 'manager'

-- All of multiple roles (AND)
participant.roles contains 'finance' and participant.roles contains 'approver'
```

### Ownership

```text
-- Entity owner
entity.ownerId == participant.id

-- Owner or shared
entity.ownerId == participant.id or entity.sharedWith contains participant.id
```

### Department-Based

```text
-- Same department
entity.department == participant.department

-- Specific department
participant.department == 'engineering'
```

### Amount Limits

```text
-- Under a fixed limit
order.amount < 50000

-- Under caller's personal limit
transfer.amount <= participant.transferLimit
```

### Status Filtering

```text
-- Specific statuses
entity.status in ['active', 'pending']

-- Exclude archived
entity.status != 'archived'

-- Exclude soft-deleted
not entity.deleted == true
```

### Combined Policies

```text
-- Finance role with amount cap
participant.roles contains 'finance' and order.amount < 50000

-- Admin or owner
participant.roles contains 'admin' or entity.ownerId == participant.id

-- Department match with classification restriction
participant.department == entity.department
    and entity.classification != 'top-secret'
```
