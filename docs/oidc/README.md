# OIDC Authentication Documentation

This section covers OpenID Connect (OIDC) authentication configuration for the Structures platform.

## Overview

Structures supports multiple OIDC identity providers for secure authentication:

- **Keycloak** - Self-hosted identity provider (recommended for development)
- **Okta** - Enterprise identity management
- **Microsoft Entra ID** (Azure AD) - Microsoft cloud identity
- **Google** - Google Workspace and consumer accounts
- **Auth0** - Universal authentication platform
- **GitHub** - Developer-focused authentication

## Documentation

### Core Implementation

- [**OIDC Implementation Guide**](./OIDC_IMPLEMENTATION.md) - Comprehensive setup guide covering:
  - Backend configuration (Spring Boot)
  - Frontend integration (Vue.js)
  - JWT token validation
  - JWKS caching
  - Multi-provider support
  - Claims mapping
  - Security best practices

### Provider-Specific Guides

#### Self-Hosted
- [**Keycloak Setup**](./README_KEYCLOAK_SETUP.md) - Local development with Keycloak

#### Enterprise Providers
- [**Okta Configuration**](./okta.md) - Okta enterprise setup
- [**Microsoft Entra ID**](./entra/) - Azure AD configuration
  - [Audience Configuration](./entra/MICROSOFT_AUDIENCE_CONFIGURATION.md)
  - [Client ID Setup](./entra/MICROSOFT_CLIENT_ID_AUDIENCE.md)
  - [Troubleshooting Guide](./entra/MICROSOFT_OIDC_TROUBLESHOOTING.md)
  - [AADSTS901002 Errors](./entra/MICROSOFT_AADSTS901002_TROUBLESHOOTING.md)

#### Social Login
- [**Social Providers**](./social/) - Social identity provider integration
  - [Microsoft Social Login](./social/microsoft-social.md)

## Quick Start

### 1. Enable OIDC in Helm Values

```yaml
oidc:
  enabled: true
  debug: false
  frontendConfigurationPath: "/api/oidc/config"
  oidcProviders:
    - provider: keycloak
      displayName: "Keycloak"
      enabled: true
      clientId: "structures-frontend"
      authority: "https://keycloak.example.com/realms/structures"
      audience: "structures-api"
      rolesClaimPath: "realm_access.roles"
```

### 2. Configure Environment Variables

```bash
OIDC_SECURITY_SERVICE_ENABLED=true
OIDC_SECURITY_SERVICE_OIDC_PROVIDERS_0_PROVIDER=keycloak
OIDC_SECURITY_SERVICE_OIDC_PROVIDERS_0_CLIENT_ID=structures-frontend
OIDC_SECURITY_SERVICE_OIDC_PROVIDERS_0_AUTHORITY=https://keycloak.example.com/realms/structures
```

### 3. Verify Configuration

```bash
curl https://your-structures-instance/api/oidc/config
```

## See Also

- [Clustering Documentation](../clustering/) - Cluster configuration
- [Kubernetes Deployment](../kubernetes/) - K8s deployment guides

---

**Last Updated**: January 2026

