# System Security

> Platform-level security architecture in Kinotic OS.

<alert type="info">

System security documentation coming soon.

</alert>

## Overview

Kinotic OS implements security at multiple layers of the platform, from network transport to fine-grained data access control.

- **Cedar Policy Hierarchies** — Authorization policies are organized across three levels: System (platform administration), Organization (team management), and Application (end-user access). Each level has its own policy scope evaluated by the Cedar policy engine.
- **OIDC Provider Configuration** — Connect external identity providers (Google, GitHub, Microsoft, Okta, or any standard OIDC provider) at the organization level. Configurations can be shared across applications within the same organization.
- **Network Security** — All communication between clients and the Kinotic server occurs over WebSocket with TLS. Internal service-to-service communication is secured within the Kubernetes cluster network.
- **TLS** — TLS termination is handled at the ingress layer with support for automated certificate management.
