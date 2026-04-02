# Deployment Workflow

> The Kinotic deployment pipeline from development to production.

## Overview

Kinotic provides a built-in deployment pipeline that takes your application from development through staging to production. The pipeline is designed around feature-based development with isolated environments and security-first build processes.

**Pipeline stages:** Development -> Staging -> Production

## Development

The development stage is optimized for rapid iteration:

- **Feature isolation** — Each feature branch gets its own development pod, so developers can test changes without affecting others.
- **Automatic deployment** — Changes deploy as code is saved, providing immediate feedback during development.
- **Firecracker VM isolation** — Builds execute in isolated Firecracker VMs for both speed and security. Each build runs in its own lightweight VM, preventing untrusted code from affecting the host or other builds.

## Staging

The staging environment acts as a gate between development and production. It operates in two phases:

### Pre-Merge Checks

Before a feature branch is merged, the staging environment runs:

- **Vulnerability scanning** — Automated security checks on dependencies and application code.
- **Migration execution tests** — Migration scripts are executed against a staging data store to verify they run correctly before reaching production.

### Post-Merge

After a feature branch is merged:

- **Full application build** — The complete application is built and deployed to the staging environment.
- **Human-in-the-loop approval** — A team member reviews the staging deployment and explicitly approves promotion to production. No automatic promotion occurs.

## Production

Production deployment happens only after staging approval:

- **Full application build** — All projects within the application are built and deployed together.
- **Deployed after approval** — The production environment is updated only when a team member has reviewed and approved the staging deployment.
