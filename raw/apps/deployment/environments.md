# Environments

> Development, staging, and production environment configuration.

<alert type="info">

This section is under active development. More details coming soon.

</alert>

## Local Development

For local development, Kinotic provides a Docker Compose setup that runs the full Kinotic OS stack on your machine. This includes:

- **Kinotic Server** — The core platform server handling services, authentication, and data persistence
- **Supporting services** — Any additional infrastructure your application depends on

The Docker Compose configuration lives in `deployment/docker-compose/` and can be started with a single command to give you a production-like environment for local testing.

## Staging

The staging environment runs on Kubernetes and mirrors production as closely as possible:

- Kubernetes-based deployment using Helm charts
- Separate data stores from production for safe migration testing
- Accessible to the development team for pre-production verification

## Production

Production is a full Kubernetes deployment managed through Helm charts:

- High-availability configuration with appropriate replica counts
- Managed data persistence
- TLS termination and ingress configuration
- Monitoring and observability integration
