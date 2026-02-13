# Structures Documentation

Welcome to the Structures platform documentation. This documentation covers configuration, deployment, and operational guides.

## Documentation Index

### 🔐 [Authentication (OIDC)](./oidc/)

OpenID Connect authentication implementation and provider configuration.

- [OIDC Implementation Guide](./oidc/OIDC_IMPLEMENTATION.md) - Comprehensive guide to OIDC setup
- [Keycloak Setup](./oidc/README_KEYCLOAK_SETUP.md) - Local Keycloak configuration
- [Okta Configuration](./oidc/okta.md) - Okta provider setup
- [Microsoft Entra ID](./oidc/entra/) - Microsoft/Azure AD configuration and troubleshooting
- [Social Login](./oidc/social/) - Social identity provider integration

### 🔄 [Clustering & High Availability](./clustering/)

Apache Ignite cluster configuration and cache management.

- [Clustering Overview](./clustering/README.md) - Getting started with clustering
- [Configuration Reference](./clustering/IGNITE_CONFIGURATION_REFERENCE.md) - All cluster properties
- [Kubernetes Tuning](./clustering/IGNITE_KUBERNETES_TUNING.md) - Advanced K8s deployment options
- [Cache Eviction Design](./clustering/CACHE_EVICTION_DESIGN.md) - Cache eviction architecture
- [Cluster Testing](./clustering/CLUSTER_TESTING.md) - Testing cluster deployments

### ☸️ [Kubernetes Deployment](./kubernetes/)

Kubernetes deployment guides and tooling.

- [KinD Quickstart](./kubernetes/QUICKSTART_CLUSTER.md) - Local Kubernetes with KinD
- [KinD Tooling](./kubernetes/KIND_TOOLING.md) - KinD cluster management scripts
- [RBAC Requirements](./kubernetes/KUBERNETES_RBAC.md) - Service account permissions
- [Cluster Configuration](./kubernetes/CLUSTER_CONFIG_ANALYSIS.md) - Helm chart configuration
- [Testing Setup](./kubernetes/CLUSTER_TESTING_SETUP.md) - Automated testing infrastructure

---

## Quick Links

| Task | Documentation |
|------|---------------|
| **Set up local development** | [Clustering README](./clustering/README.md) |
| **Configure OIDC authentication** | [OIDC Implementation](./oidc/OIDC_IMPLEMENTATION.md) |
| **Deploy to Kubernetes** | [Kubernetes Tuning](./clustering/IGNITE_KUBERNETES_TUNING.md) |
| **Test cluster setup** | [Cluster Testing](./clustering/CLUSTER_TESTING.md) |
| **Use KinD for local K8s** | [KinD Quickstart](./kubernetes/QUICKSTART_CLUSTER.md) |

---

## Configuration Property Prefixes

| Prefix | Purpose |
|--------|---------|
| `kinotic.cluster.*` | Ignite cluster configuration |
| `structures.*` | Structures-specific settings |
| `oidc.security-service.*` | OIDC authentication |

---

**Last Updated**: January 2026

