# Access Control

> Attribute-Based Access Control (ABAC) for published services and entity data.

## Overview

Kinotic provides a unified policy expression language for controlling access to both **published service methods** and **entity data**. The same `@AbacPolicy` expression syntax works in both contexts — the platform routes enforcement to the appropriate layer based on where the policy is applied.

<table>
<thead>
  <tr>
    <th>
      Placement
    </th>
    
    <th>
      Enforcement Point
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Service method
    </td>
    
    <td>
      Before invocation -- the call is rejected if the policy fails
    </td>
  </tr>
  
  <tr>
    <td>
      Entity decorator
    </td>
    
    <td>
      At the data layer -- unauthorized records are never returned
    </td>
  </tr>
</tbody>
</table>

## Expression Language

Policy expressions use a simple, developer-friendly syntax with dotted attribute paths, comparison operators, and boolean logic.

### Attribute Paths

Expressions reference attributes using dotted notation. The root identifier determines what the path resolves against:

- **participant** — the authenticated caller (roles, department, limits, etc.)
- **context** — the request environment (time, IP, etc.)
- **Any other root** — resolved against method parameter names (service methods) or the entity being accessed (entity decorators)

### Operators

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
      Pattern match
    </td>
    
    <td>
      <code>
        entity.email like '*@kinotic.ai'
      </code>
    </td>
  </tr>
</tbody>
</table>

### Boolean Logic

Combine conditions with `and`, `or`, and `not`. Parentheses override default precedence. Keywords are case-insensitive.

**Precedence** (highest to lowest): comparisons, `not`, `and`, `or`

```text
participant.roles contains 'finance' and order.amount < 50000

entity.status in ['active', 'pending'] or participant.department == entity.department

not entity.deleted == true

(participant.roles contains 'admin' or participant.roles contains 'manager')
    and entity.classification != 'top-secret'
```

### Literals

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
</tbody>
</table>

## Service Method Policies

Apply `@AbacPolicy` to published service methods to enforce authorization before the call reaches the service. The platform evaluates the policy against the method's arguments and the caller's identity.

```typescript
import { AbacPolicy, Publish } from '@kinotic-ai/core'

@Publish('com.example')
class OrderService {

    @AbacPolicy("participant.roles contains 'finance' and order.amount < 50000")
    placeOrder(order: Order): void {
        // Only reached if the caller has the 'finance' role
        // AND the order amount is under 50,000
    }

    @AbacPolicy("participant.roles contains 'finance'")
    @AbacPolicy("transfer.amount <= participant.transferLimit")
    transferFunds(transfer: Transfer, approval: Approval): void {
        // Multiple policies are combined with AND semantics —
        // both must be satisfied
    }
}
```

### Parameter Name Resolution

In service method policies, non-reserved root identifiers are resolved against method parameter names. For a method like `transferFunds(transfer: Transfer, approval: Approval)`:

<table>
<thead>
  <tr>
    <th>
      Expression Path
    </th>
    
    <th>
      Resolves To
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        transfer.amount
      </code>
    </td>
    
    <td>
      The <code>
        amount
      </code>
      
       field of the first argument
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        approval.approved
      </code>
    </td>
    
    <td>
      The <code>
        approved
      </code>
      
       field of the second argument
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        participant.roles
      </code>
    </td>
    
    <td>
      The caller's roles from the security context
    </td>
  </tr>
</tbody>
</table>

## Entity Policies

Apply `@AbacPolicy` to entities via `@EntityServiceDecorators` to enforce authorization at the data layer. Unauthorized records are never returned -- the platform filters them before they reach your code.

```typescript
import { Entity, AutoGeneratedId } from '@kinotic-ai/persistence'
import { EntityServiceDecorators, $AbacPolicy } from '@kinotic-ai/persistence'

@EntityServiceDecorators({
    allRead: [
        $AbacPolicy("entity.sharedWith contains participant.id")
    ],
    allCreate: [
        $AbacPolicy("participant.roles contains 'editor'")
    ],
    allDelete: [
        $AbacPolicy("participant.roles contains 'admin'")
    ]
})
@Entity()
export class Photo {
    @AutoGeneratedId
    id: string | null = null
    title: string = ''
    ownerId: string = ''
    sharedWith: string[] = []
}
```

### Operation Groups

<table>
<thead>
  <tr>
    <th>
      Group
    </th>
    
    <th>
      Operations Covered
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        allCreate
      </code>
    </td>
    
    <td>
      <code>
        save
      </code>
      
      , <code>
        bulkSave
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        allRead
      </code>
    </td>
    
    <td>
      <code>
        findById
      </code>
      
      , <code>
        findByIds
      </code>
      
      , <code>
        findAll
      </code>
      
      , <code>
        search
      </code>
      
      , <code>
        count
      </code>
      
      , <code>
        countByQuery
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        allUpdate
      </code>
    </td>
    
    <td>
      <code>
        update
      </code>
      
      , <code>
        bulkUpdate
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        allDelete
      </code>
    </td>
    
    <td>
      <code>
        deleteById
      </code>
      
      , <code>
        deleteByQuery
      </code>
    </td>
  </tr>
</tbody>
</table>

You can also target individual operations:

```typescript
@EntityServiceDecorators({
    findAll: [
        $AbacPolicy("entity.status in ['active', 'pending']")
    ],
    deleteById: [
        $AbacPolicy("participant.roles contains 'admin'")
    ]
})
```

### Dynamic Resource Sharing

Entity policies enable user-driven access control without creating dynamic policies. The policies themselves stay static — only the entity data changes.

**Example: Photo sharing**

The policy `entity.sharedWith contains participant.id` never changes. When a user shares a photo, the application updates the photo's `sharedWith` array. The next time anyone queries photos, the policy filters results to only include photos shared with the caller.

```typescript
class PhotoService {
    async sharePhoto(photoId: string, userId: string): Promise<void> {
        const photo = await this.photoEntityService.findById(photoId)
        photo.sharedWith.push(userId)
        await this.photoEntityService.update(photo)
    }

    // No authorization code here — the EntityService policy handles it
    async getMyPhotos(): Promise<Photo[]> {
        return await this.photoEntityService.findAll({ page: 0, size: 100 })
    }
}
```

## Combining Both Layers

Service method policies and entity policies work together:

```typescript
// Service-level enforcement — can this caller use this service at all?
@Publish('com.example')
class PhotoService {

    @AbacPolicy("participant.roles contains 'user'")
    async sharePhoto(photoId: string, userId: string): Promise<void> {
        const photo = await this.photoEntityService.findById(photoId)
        photo.sharedWith.push(userId)
        await this.photoEntityService.update(photo)
    }
}

// Persistence enforcement — which photos can this caller see?
@EntityServiceDecorators({
    allRead: [
        $AbacPolicy("entity.ownerId == participant.id or entity.sharedWith contains participant.id")
    ],
    allCreate: [
        $AbacPolicy("participant.roles contains 'user'")
    ],
    allDelete: [
        $AbacPolicy("entity.ownerId == participant.id")
    ]
})
@Entity()
export class Photo {
    @AutoGeneratedId
    id: string | null = null
    ownerId: string = ''
    sharedWith: string[] = []
}
```

In this example:

- The platform ensures only authenticated users with the `'user'` role can call `sharePhoto`
- The persistence layer ensures users only see photos they own or that are shared with them
- Only the photo owner can delete their photos
- The service code contains zero authorization logic — the platform handles it

## Role-Based Access Control

ABAC policies can express pure role-based checks:

```typescript
// Only admins
@AbacPolicy("participant.roles contains 'admin'")

// Admin or manager
@AbacPolicy("participant.roles contains 'admin' or participant.roles contains 'manager'")

// Must have both roles
@AbacPolicy("participant.roles contains 'finance' and participant.roles contains 'approver'")
```

RBAC is a subset of ABAC — no separate role-checking mechanism is needed.
