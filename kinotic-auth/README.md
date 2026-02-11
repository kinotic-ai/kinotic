# Structures Auth Library

A comprehensive JWT authentication and authorization library for the Structures framework.

## Overview

The Structures Auth library provides JWT token management, OIDC integration, and authorization services for Spring Boot applications. It follows the standard Structures library conventions and integrates seamlessly with the broader Structures ecosystem.

## Features

- **JWT Token Management**: Create, validate, and parse JWT tokens
- **OIDC Integration**: OpenID Connect authentication support
- **Authorization Services**: Role-based access control and permission management
- **Spring Boot Auto-Configuration**: Automatic configuration for Spring Boot applications
- **Security Best Practices**: Secure token handling and validation

## Dependencies

- **JJWT**: JWT token creation and validation
- **Jackson**: JSON processing for token claims
- **Continuum Core**: Framework integration
- **Spring Boot**: Auto-configuration and dependency injection

## Quick Start

### 1. Add the dependency

```gradle
implementation project(':structures-auth')
```

### 2. Enable auto-configuration

The library automatically configures OIDC security when the `oidc-security-service.enabled` property is set to `true`. No additional annotations are required.

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### 3. Configure OIDC providers

```yaml
oidc-security-service:
  enabled: true
  debug: false
  tenant-id-field-name: "tenantId"
  frontend-configuration-path: "/app-config.override.json"
  oidc-providers:
    - provider: "keycloak"
      display-name: "Keycloak"
      enabled: true
      client-id: "structures-client"
      authority: "http://localhost:8888/auth/realms/test"
      redirect-uri: "http://localhost:5173/login"
      post-logout-redirect-uri: "http://localhost:5173"
      silent-redirect-uri: "http://localhost:5173/login/silent-renew"
      domains:
        - "example.com"
      audience: "structures-client"
      roles-claim-path: "realm_access.roles"
      additional-scopes: "groups"
```

### 4. Use the services

```java
@Service
public class MyService {
    
    @Autowired
    private SecurityService securityService;
    
    public void processRequest(HttpServletRequest request) {
        // OIDC token validation is automatic
        String userId = securityService.getCurrentUserId();
        Set<String> roles = securityService.getCurrentUserRoles();
        
        if (roles.contains("admin")) {
            // Perform admin operations
        }
    }
}
```

## Configuration

The library provides sensible defaults but can be customized through Spring Boot configuration properties:

```yaml
oidc-security-service:
  enabled: true
  debug: false
  tenant-id-field-name: "tenantId"
  frontend-configuration-path: "/app-config.override.json"
  oidc-providers:
    - provider: "keycloak"
      display-name: "Keycloak"
      enabled: true
      client-id: "structures-client"
      authority: "http://localhost:8888/auth/realms/test"
      redirect-uri: "http://localhost:5173/login"
      post-logout-redirect-uri: "http://localhost:5173"
      silent-redirect-uri: "http://localhost:5173/login/silent-renew"
      domains:
        - "example.com"
      audience: "structures-client"
      roles-claim-path: "realm_access.roles"
      additional-scopes: "groups"
    
    - provider: "okta"
      display-name: "Okta"
      enabled: true
      client-id: "your-okta-client-id"
      authority: "https://your-okta-domain.okta.com/oauth2/default"
      redirect-uri: "http://localhost:5173/login"
      post-logout-redirect-uri: "http://localhost:5173"
      silent-redirect-uri: "http://localhost:5173/login/silent-renew"
      domains:
        - "yourcompany.com"
      audience: "your-okta-client-id"
      roles-claim-path: "roles"
      additional-scopes: "groups"
```

## API Reference

### SecurityService

- `getCurrentUserId()`: Gets the current authenticated user ID
- `getCurrentUserRoles()`: Gets all roles for the current user
- `hasRole(String role)`: Checks if the current user has a specific role
- `hasAnyRole(Set<String> roles)`: Checks if the current user has any of the specified roles
- `isAuthenticated()`: Checks if the current user is authenticated
- `getCurrentUserClaims()`: Gets all claims for the current user

### OIDC Configuration

- **Automatic JWT Validation**: JWT tokens are automatically validated on each request
- **Multi-Provider Support**: Support for multiple OIDC providers simultaneously
- **JWKS Caching**: Efficient JSON Web Key Set caching for performance
- **Role Extraction**: Automatic role extraction from JWT claims

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Master switch for OIDC security service |
| `debug` | boolean | `false` | Enable debug logging and UI debugging |
| `tenant-id-field-name` | string | `"tenantId"` | JWT claim field name for tenant ID |
| `frontend-configuration-path` | string | `"/app-config.override.json"` | Path for frontend configuration overrides |
| `oidc-providers` | array | `[]` | List of OIDC provider configurations |

#### OIDC Provider Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `provider` | string | Yes | Unique identifier for the provider |
| `display-name` | string | Yes | Human-readable provider name |
| `enabled` | boolean | Yes | Enable/disable this specific provider |
| `client-id` | string | Yes | OAuth client ID from the provider |
| `authority` | string | Yes | OIDC issuer authority URL |
| `redirect-uri` | string | Yes | OAuth redirect URI after authentication |
| `post-logout-redirect-uri` | string | Yes | Redirect URI after logout |
| `silent-redirect-uri` | string | Yes | URI for silent token renewal |
| `domains` | array | Yes | Email domains this provider handles |
| `audience` | string | Yes | Expected audience claim in JWT tokens |
| `roles-claim-path` | string | No | JSON path to roles claim in JWT |
| `additional-scopes` | string | No | Additional OAuth scopes to request |
| `roles` | array | No | Default roles for this provider |
| `metadata` | object | No | Additional provider metadata |

## Security Considerations

- **JWT Validation**: All JWT tokens are automatically validated for signature, expiration, and claims
- **Issuer Verification**: Only tokens from configured, trusted issuers are accepted
- **Audience Validation**: Tokens must have valid audience claims for your application
- **Role-Based Access**: Implement role-based access control using extracted JWT claims
- **HTTPS Required**: Always use HTTPS in production for secure token transmission
- **Token Storage**: Store tokens securely in memory, never in localStorage or cookies
- **Regular Updates**: Keep OIDC provider configurations and JWKS endpoints up to date

## Testing

The library includes comprehensive tests:

```bash
./gradlew :structures-auth:test
```

## Contributing

1. Follow the existing code conventions
2. Add tests for new functionality
3. Update documentation as needed
4. Ensure security best practices are followed
5. Test with multiple OIDC providers
6. Validate JWT token handling thoroughly

## How It Works

### 1. **JWT Token Extraction**
- Automatically extracts JWT tokens from `Authorization: Bearer <token>` headers
- Supports both access tokens and ID tokens

### 2. **Token Validation**
- Validates JWT signature using JWKS from the issuer
- Verifies issuer against configured OIDC providers
- Checks audience claims against provider configuration
- Validates token expiration

### 3. **User Creation**
- Creates `Participant` objects from JWT claims
- Extracts user information (email, name, roles)
- Maps email domains to appropriate OIDC providers
- Applies role-based access control

### 4. **Frontend Integration**
- Serves configuration overrides at `/app-config.override.json`
- Enables dynamic frontend configuration without rebuilds
- Supports runtime provider enable/disable

## Examples

### Keycloak Configuration
```yaml
oidc-security-service:
  enabled: true
  oidc-providers:
    - provider: "keycloak"
      display-name: "Keycloak"
      enabled: true
      client-id: "structures-client"
      authority: "http://localhost:8888/auth/realms/test"
      redirect-uri: "http://localhost:5173/login"
      post-logout-redirect-uri: "http://localhost:5173"
      silent-redirect-uri: "http://localhost:5173/login/silent-renew"
      domains:
        - "example.com"
      audience: "structures-client"
      roles-claim-path: "realm_access.roles"
```

### Okta Configuration
```yaml
oidc-security-service:
  enabled: true
  oidc-providers:
    - provider: "okta"
      display-name: "Okta"
      enabled: true
      client-id: "0oaowrlsm5Ua1vWD85d7"
      authority: "https://dev-39125344.okta.com/oauth2/default"
      redirect-uri: "http://localhost:5173/login"
      post-logout-redirect-uri: "http://localhost:5173"
      silent-redirect-uri: "http://localhost:5173/login/silent-renew"
      domains:
        - "yourcompany.com"
      audience: "0oaowrlsm5Ua1vWD85d7"
      roles-claim-path: "roles"
```

## Related Documentation

For detailed OIDC configuration and troubleshooting, see:
- [OIDC Implementation Guide](oidc-docs/OIDC_IMPLEMENTATION.md)
- [Provider-Specific Guides](oidc-docs/) - Okta, Microsoft, Keycloak, and more
- [Frontend Configuration](../structures-frontend-next/CONFIGURATION.md)

## License

This library is part of the Structures framework and follows the same licensing terms. 