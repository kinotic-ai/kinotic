# CRI Format

> Kinotic Resource Identifier specification.

## Overview

A CRI (Kinotic Resource Identifier) is used by Kinotic to route requests to the appropriate service, method, or event stream. It is a URI-like format with parts named for clarity within the Kinotic platform.

## Format

```text
scheme://[scope@]resourceName[/path][#version]
```

Everything in brackets (`[]`) is optional.

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
      Event streams
    </td>
  </tr>
</tbody>
</table>

### Scope

An optional qualifier that narrows the CRI to a specific context, such as a tenant ID, user ID, or device ID. When present, it appears before the `@` symbol.

If a scope needs sub-scopes, use the format `scope:sub-scope`.

```text
srv://tenant-123@com.example.OrderService
stream://device-42@temperature/sensor-1
```

### Resource Name

The name of the resource being addressed. For services, this is the fully qualified service name. For streams, this is the event type name.

```text
srv://com.example.UserService
stream://temperature
```

### Path

An optional path that identifies a specific part of the resource, such as a method name on a service.

```text
srv://com.example.UserService/findById
stream://temperature/sensor-1
```

### Version

An optional semantic version for the resource. Enables versioned service routing so multiple versions of a service can coexist.

```text
srv://com.example.UserService/findById#1.0.0
srv://com.example.UserService#2.0.0
```

## Factory Function

The `createCRI` function provides several overloads for constructing CRI instances:

```typescript
import { createCRI } from '@kinotic-ai/core'

// From a raw string
const cri1 = createCRI('srv://com.example.UserService/findById#1.0.0')

// From scheme and resource name
const cri2 = createCRI('srv', 'com.example.UserService')

// From scheme, scope, and resource name
const cri3 = createCRI('stream', 'tenant-123', 'orders')

// From all components
const cri4 = createCRI('srv', null, 'com.example.UserService', 'findById', '1.0.0')
```

### CRI Interface

The `CRI` interface provides methods to access each component:

```typescript
const cri = createCRI('srv://tenant-123@com.example.OrderService/placeOrder#2.0.0')

cri.scheme()        // 'srv'
cri.scope()         // 'tenant-123'
cri.hasScope()      // true
cri.resourceName()  // 'com.example.OrderService'
cri.path()          // 'placeOrder'
cri.hasPath()       // true
cri.version()       // '2.0.0'
cri.hasVersion()    // true
cri.baseResource()  // 'srv://tenant-123@com.example.OrderService'
cri.raw()           // 'srv://tenant-123@com.example.OrderService/placeOrder#2.0.0'
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
        srv://com.example.UserService
      </code>
    </td>
    
    <td>
      A published service
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        srv://com.example.UserService/findById
      </code>
    </td>
    
    <td>
      A specific method on a service
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        srv://com.example.UserService/findById#1.0.0
      </code>
    </td>
    
    <td>
      A versioned service method
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        stream://temperature
      </code>
    </td>
    
    <td>
      An event stream for temperature data
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        stream://temperature/sensor-1
      </code>
    </td>
    
    <td>
      A specific path within a stream
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        stream://tenant-123@orders/placed
      </code>
    </td>
    
    <td>
      A scoped event stream
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        srv://tenant-123@com.example.OrderService
      </code>
    </td>
    
    <td>
      A scoped service
    </td>
  </tr>
</tbody>
</table>
