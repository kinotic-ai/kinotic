# CRI Format

> Kinotic Resource Identifier specification.

## Overview

A CRI (Kinotic Resource Identifier) is used by Kinotic to route requests to the appropriate service, method, or event stream. It is a URI-like format with parts named for clarity within the Kinotic platform.

## Format

```text
scheme://[scope@][zone~]resourceName[/path][#version]
```

Everything in brackets (`[]`) is optional.

## Case

Every part keeps the case it was written with, and addresses match by exact string comparison — `OrderService` and `OrderService` are two different addresses. Service names carry the case of the class that published them, so a typical address looks like:

```text
srv://os-api~org.kinotic.os.api.services.ProjectService/findByRepoFullName
```

## Components

### Scheme

Identifies the type of resource being addressed.

<table>
<thead>
  <tr>
    <th>
      Scheme
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
        srv
      </code>
    </td>
    
    <td>
      Published services and their methods
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        stream
      </code>
    </td>
    
    <td>
      Event streams (not yet routable through the gateway)
    </td>
  </tr>
</tbody>
</table>

### Scope

An optional qualifier that narrows the CRI to a specific context, such as a tenant ID, user ID, or device ID. When present, it appears before the `@` symbol.

If a scope needs sub-scopes, use the format `scope:sub-scope`.

```text
srv://tenant-123@app.acme-org.orders-app~OrderService
stream://device-42@app.acme-org.orders-app~temperature/sensor-1
```

### Zone

The optional zone places the resource in the isolation boundary the gateway validates on every send and subscribe. It precedes the resourceName, separated by `~`. A zone is one or more dot-separated labels of lowercase letters, digits, and interior dashes.

<table>
<thead>
  <tr>
    <th>
      Zone
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
        app.<organizationId>.<applicationId>
      </code>
    </td>
    
    <td>
      One application's services. Only that application (and system participants) can call them; they are hosted by the owning organization's runtime, which authenticates as an organization participant. Applications may nest their own sub-zones, e.g. <code>
        app.acme-org.orders-app.billing
      </code>
      
      .
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        app-api
      </code>
    </td>
    
    <td>
      The platform's data plane for applications, such as entity persistence and named query execution. Hosted in-process by the platform only.
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        os-api
      </code>
    </td>
    
    <td>
      The platform services organizations manage the system through, such as member, application, and entity definition management. Hosted in-process by the platform only.
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        system
      </code>
    </td>
    
    <td>
      Platform-internal services. Only system participants can call or host them.
    </td>
  </tr>
</tbody>
</table>

Organization and application ids become zone labels, but only ever after the `app` prefix, so they cannot collide with the platform's own zones. The one exception is guarded explicitly: creating an organization or application whose name slugifies to `system` is rejected.

Which zones a connection may address is determined by the authenticated participant. A participant may also address any sub-zone of a zone it is allowed, so `app.acme-org.orders-app.billing` is reachable by whoever may reach `app.acme-org.orders-app`. An address without a `~` carries no zone at all and is only reachable by system participants.

<table>
<thead>
  <tr>
    <th>
      Participant
    </th>
    
    <th>
      May send to
    </th>
    
    <th>
      May subscribe to
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      APPLICATION (org, app)
    </td>
    
    <td>
      <code>
        app-api
      </code>
      
      , <code>
        app.<org>.<app>
      </code>
    </td>
    
    <td>
      reply destinations only
    </td>
  </tr>
  
  <tr>
    <td>
      ORGANIZATION (org)
    </td>
    
    <td>
      <code>
        os-api
      </code>
      
      , <code>
        app-api
      </code>
      
      , <code>
        app.<org>.*
      </code>
    </td>
    
    <td>
      <code>
        app.<org>.*
      </code>
      
       — its applications' zones
    </td>
  </tr>
  
  <tr>
    <td>
      SYSTEM
    </td>
    
    <td>
      everything
    </td>
    
    <td>
      <code>
        system
      </code>
    </td>
  </tr>
</tbody>
</table>

### Resource Name

The name of the resource being addressed, excluding the zone. For services, this is the fully qualified service name. For streams, this is the event type name.

```text
srv://os-api~com.example.UserService
stream://app.acme-org.orders-app~temperature
```

### Path

An optional path that identifies a specific part of the resource, such as a method name on a service. The path's case is preserved.

```text
srv://os-api~com.example.UserService/findById
stream://app.acme-org.orders-app~temperature/sensor-1
```

### Version

An optional semantic version for the resource. Enables versioned service routing so multiple versions of a service can coexist.

```text
srv://os-api~com.example.UserService/findById#1.0.0
srv://os-api~com.example.UserService#2.0.0
```

## Factory Function

The `createCRI` function provides several overloads for constructing CRI instances:

```typescript
import { createCRI } from '@kinotic-ai/core'

// From a raw string
const cri1 = createCRI('srv://os-api~com.example.UserService/findById#1.0.0')

// From scheme and resource name (which may carry a zone)
const cri2 = createCRI('srv', 'os-api~com.example.UserService')

// From scheme, scope, and resource name
const cri3 = createCRI('stream', 'tenant-123', 'app.acme-org.orders-app~orders')

// From all components
const cri4 = createCRI('srv', null, 'os-api~com.example.UserService', 'findById', '1.0.0')
```

### CRI Interface

The `CRI` interface provides methods to access each component:

```typescript
const cri = createCRI('srv://tenant-123@app.acme-org.orders-app~OrderService/placeOrder#2.0.0')

cri.scheme()        // 'srv'
cri.scope()         // 'tenant-123'
cri.hasScope()      // true
cri.zone()          // 'app.acme-org.orders-app'
cri.hasZone()       // true
cri.resourceName()  // 'OrderService'
cri.path()          // 'placeOrder'
cri.hasPath()       // true
cri.version()       // '2.0.0'
cri.hasVersion()    // true
cri.baseResource()  // 'srv://tenant-123@app.acme-org.orders-app~OrderService'
cri.raw()           // 'srv://tenant-123@app.acme-org.orders-app~OrderService/placeOrder#2.0.0'
```

## Examples

<table>
<thead>
  <tr>
    <th>
      CRI
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
        srv://os-api~org.kinotic.os.api.services.security.MemberService
      </code>
    </td>
    
    <td>
      A platform service in the <code>
        os-api
      </code>
      
       zone
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        srv://app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository/save
      </code>
    </td>
    
    <td>
      A specific method on a platform service
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        srv://app.acme-org.orders-app~OrderService/create#1.0.0
      </code>
    </td>
    
    <td>
      A versioned method on an application's own service
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        srv://app.acme-org.orders-app.billing~InvoiceService
      </code>
    </td>
    
    <td>
      A service in an application-declared sub-zone
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        srv://system~org.kinotic.orchestrator.api.services.WorkloadOrchestrationService
      </code>
    </td>
    
    <td>
      A platform-internal service
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        stream://app.acme-org.orders-app~temperature
      </code>
    </td>
    
    <td>
      An application's event stream
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        srv://node1@system~kinotic-ai.vm-manager.VmManager
      </code>
    </td>
    
    <td>
      A scoped system service targeting one node
    </td>
  </tr>
</tbody>
</table>
