# System Architecture

> High-level architecture of the Kinotic OS platform.

## Overview

Kinotic OS is a full-stack application platform that handles service communication, persistence, authentication, authorization, deployment, and observability. The system is composed of several core components that work together to provide a seamless development and runtime experience.

## Core Components

- **Kinotic Server** — The central platform server built on Spring Boot. Manages service registration, authentication, session management, and coordinates all platform operations.
- **RPC Gateway** — Routes remote procedure calls between clients and published services over STOMP/WebSocket. Enforces ABAC policies at the gateway layer before calls reach service implementations.
- **Persistence Layer** — Provides automatic CRUD operations for entities backed by Elasticsearch. Compiles ABAC policies into query filters so unauthorized data is never returned.
- **Auth System** — Handles authentication (email/password and OIDC) and authorization (Cedar policy engine). Supports three authorization hierarchies: System, Organization, and Application.

## Tech Stack

<table>
<thead>
  <tr>
    <th>
      Component
    </th>
    
    <th>
      Technology
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Orchestration
    </td>
    
    <td>
      Kubernetes
    </td>
  </tr>
  
  <tr>
    <td>
      Policy Engine
    </td>
    
    <td>
      Cedar
    </td>
  </tr>
  
  <tr>
    <td>
      Build Isolation
    </td>
    
    <td>
      Firecracker VMs
    </td>
  </tr>
  
  <tr>
    <td>
      Runtime
    </td>
    
    <td>
      Bun
    </td>
  </tr>
  
  <tr>
    <td>
      Database
    </td>
    
    <td>
      Postgres (Hibernate Reactive)
    </td>
  </tr>
  
  <tr>
    <td>
      Search/Persistence
    </td>
    
    <td>
      Elasticsearch
    </td>
  </tr>
  
  <tr>
    <td>
      Logging
    </td>
    
    <td>
      Grafana Loki
    </td>
  </tr>
  
  <tr>
    <td>
      Payments
    </td>
    
    <td>
      Stripe Connect
    </td>
  </tr>
</tbody>
</table>

## Communication

Services communicate via STOMP over WebSocket, with messages routed by CRI (Kinotic Resource Identifier). Each service, method, and event stream is addressable through a CRI, which follows the format:

```text
scheme://[scope@]resourceName[/path][#version]
```

The RPC gateway uses CRIs to route requests to the correct service instance, apply versioning, and enforce scope-based multi-tenancy. See the [CRI Format](/reference/cri-format) reference for details.
