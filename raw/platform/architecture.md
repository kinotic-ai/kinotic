# System Architecture

> High-level architecture of the Kinotic OS platform.

## Overview

Kinotic OS is a full-stack application platform that handles service communication, persistence, authentication, authorization, deployment, and observability. The system is composed of several core components that work together to provide a seamless development and runtime experience.

## Core Components

- **Management server** (`kinotic-server-management`) — The API the portal, the Kinotic CLI and organization members' MCP hosts call: sign-up, logins and invitations, the `management-api` services, and the GitHub App's webhook.
- **System server** (`kinotic-server-system`) — The API the system console and the vm-managers call: the `system-api` services, project deployments, and the cluster singletons that reconcile deployments and delete old workload runs.
- **App server** (`kinotic-server-app`) — The API every application's users, UIs and microservices call: the `app-api` persistence services and the services applications publish in their own zones, each application at its own API host.
- **RPC Gateway** — Each server's gateway routes remote procedure calls between its clients and published services over STOMP/WebSocket, and serves the server's REST and MCP endpoints.
- **Persistence Layer** — Provides automatic CRUD operations for entities backed by Elasticsearch.
- **Auth System** — Handles authentication (email/password and OIDC) across three authorization hierarchies: System, Organization, and Application. Each server admits only the participants it serves.

The three servers are Spring Boot applications that form one Ignite cluster, in which each hosts only its own zones. [Defense in Depth](/platform/defense-in-depth#network-architecture) shows which participants each admits and which zones each hosts.

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

The RPC gateway uses CRIs to route requests to the correct service instance, apply versioning, and enforce scope-based multi-tenancy. See the [CRI Format](/platform/reference/cri-format) reference for details.

### Never block the event loop

A Java service published with `@Publish` is invoked on a Vert.x event loop it shares with the other services on the node, the same rule Vert.x sets for its own handlers. A method returns a `Future`, `CompletableFuture`, `Mono` or `Flux` and lets the work complete off the loop; a method that is slow, because it blocks on a synchronous client, a lock or a sleep, or because it does heavy CPU work, hands that work off inside itself:

```java
@Override
public Future<Report> render(String id) {
    return vertx.executeBlocking(() -> renderer.render(id));   // the loop is free while the worker runs
}
```

The platform never hands off on a service's behalf. A method that blocks stalls every service sharing its loop, and Vert.x's blocked-thread checker reports it in the log.
