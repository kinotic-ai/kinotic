# OIDC Authentication Implementation

This document describes the OIDC (OpenID Connect) authentication implementation for the Structures project.

## Overview

The OIDC implementation provides JWT token-based authentication with the following features:

- **JWKS Caching**: Efficient caching of JSON Web Key Sets using Caffeine
- **Issuer Validation**: Validates JWT tokens against configured allowed issuers
- **Audience Validation**: Validates JWT tokens against configured allowed audiences
- **Token Expiration**: Automatically checks token expiration
- **Well-known Configuration**: Fetches and caches OIDC provider configuration
- **Role Extraction**: Extracts user roles from JWT claims
- **Tenant Support**: Supports multi-tenant applications with tenant ID extraction
- **Frontend Integration**: Complete Vue.js frontend integration with navigation support
- **Multi-Provider Support**: Supports Okta, Keycloak, Google, Microsoft, and custom providers
- **State Management**: Robust state handling with base64 encoding and localStorage persistence

## Components

### 1. JwksService
Handles JWKS (JSON Web Key Set) operations:
- Fetches well-known OIDC configuration
- Caches JWKS keys and configurations
- Extracts key information from JWT tokens
- Provides efficient key lookup by issuer and key ID

### 2. OidcAuthVerifier
Main authentication component:
- Implements SecurityService interface
- Validates JWT tokens using JJWT 0.12.x
- Creates Participant objects from JWT claims
- Handles issuer and audience validation
- Supports multiple audience validation

### 3. OidcAuthVerifierProperties
Configuration properties for OIDC:
- `enabled`: Enable/disable OIDC authentication
- `allowedIssuers`: List of allowed OIDC issuers
- `authorizationAudiences`: List of allowed audiences

### 4. Frontend Integration
Vue.js frontend components:
- OIDC login flow with multiple providers (Okta, Keycloak, Google, Microsoft)
- Automatic token handling and refresh
- Navigation after successful authentication
- Error handling and user feedback
- State management with localStorage persistence
- **Conditional display of login buttons based on enabled providers**

## Conditional OIDC Button Display

The frontend login page (`structures-frontend-next/src/pages/login/Login.vue`) automatically shows/hides OIDC login buttons based on the enabled configuration properties.

### How It Works

1. **Provider Configuration**: Each OIDC provider has an `enabled` flag in the configuration
2. **Environment Variables**: The enabled flags are controlled by environment variables
3. **Conditional Rendering**: The login page checks these flags and only shows enabled providers
4. **Dynamic UI**: The "OR" separator only appears if at least one OIDC provider is enabled

### Configuration

**Environment Variables for Frontend:**
```bash
# Enable/disable specific providers
VITE_OIDC_OKTA_ENABLED=true
VITE_OIDC_KEYCLOAK_ENABLED=true
VITE_OIDC_GOOGLE_ENABLED=false
VITE_OIDC_MICROSOFT_ENABLED=false
VITE_OIDC_GITHUB_ENABLED=false
VITE_OIDC_CUSTOM_ENABLED=false
```

**Frontend Logic:**
```typescript
// Check if a specific provider is enabled
isProviderEnabled(provider: OidcProvider): boolean {
  const config = getProviderConfig(provider);
  return config.enabled;
}

// Check if any OIDC providers are enabled
get hasEnabledOidcProviders(): boolean {
  const providers: OidcProvider[] = ['okta', 'keycloak', 'google', 'microsoft', 'github', 'custom'];
  return providers.some(provider => this.isProviderEnabled(provider));
}
```

### Example Scenarios

**Scenario 1: Only Okta Enabled**
- Shows: Username/Password form + "Continue with Okta" button
- Hides: All other OIDC buttons
- Shows: "OR" separator between forms

**Scenario 2: Multiple Providers Enabled**
- Shows: Username/Password form + enabled provider buttons
- Hides: Disabled provider buttons
- Shows: "OR" separator between forms

**Scenario 3: No OIDC Providers Enabled**
- Shows: Only Username/Password form
- Hides: All OIDC buttons and "OR" separator

### Benefits

- **Clean UI**: Only shows relevant login options
- **Easy Configuration**: Control via environment variables
- **Flexible Deployment**: Different providers for different environments
- **User Experience**: Reduces confusion by hiding unavailable options

## Configuration

### Application Properties

Add the following configuration to your `application.yml`:

```yaml
oidc-security-service:
  enabled: true
  debug: true
  oidc-providers:
    - provider: "your-provider-name"
      display-name: "Your Provider"
      enabled: true
      roles-claim-path: "realm_access.roles"
      domains:
        - "your-domain.com"
      audience: "your-application-client-id"
      client-id: "your-application-client-id"
      authority: "https://your-oidc-provider.com"
      redirect-uri: "http://localhost:9090/login"
      post-logout-redirect-uri: "http://localhost:9090"
      silent-redirect-uri: "http://localhost:9090/login/silent-renew"
      roles:
        - "user"
```

### Example Configurations

#### Okta (Tested and Verified)
**⚠️ IMPORTANT**: Okta access tokens MUST include an email claim. See [Okta Configuration Guide](./okta.md) for detailed setup instructions.

```yaml
oidc-security-service:
  enabled: true
  debug: true
  oidc-providers:
    - provider: "okta"
      display-name: "Okta"
      enabled: true
      roles-claim-path: "realm_access.roles"
      domains:
        - "your-domain.com"
      audience: "0oaowrlsm5Ua1vWD85d7"
      client-id: "0oaowrlsm5Ua1vWD85d7"
      authority: "https://dev-39125344.okta.com/oauth2/default"
      redirect-uri: "http://localhost:9090/login"
      post-logout-redirect-uri: "http://localhost:9090"
      silent-redirect-uri: "http://localhost:9090/login/silent-renew"
      roles:
        - "user"
```

**Frontend Configuration (structures-frontend-next):**
```typescript
// In OidcConfiguration.ts
okta: {
  client_id: '0oaowrlsm5Ua1vWD85d7',
  client_secret: '',
  authority: 'https://dev-39125344.okta.com/oauth2/default',
  redirect_uri: 'http://localhost:5173/login',
  post_logout_redirect_uri: 'http://localhost:5173',
  silent_redirect_uri: 'http://localhost:5173/login/silent-renew',
  loadUserInfo: true,
  scope: 'openid profile email', // email scope is REQUIRED
  publicClient: {
    isPublicClient: true,
    responseType: 'code',
    responseMode: 'query'
  },
  metadata: {
    authorization_endpoint: 'https://dev-39125344.okta.com/oauth2/default/v1/authorize',
    token_endpoint: 'https://dev-39125344.okta.com/oauth2/default/v1/token',
    userinfo_endpoint: 'https://dev-39125344.okta.com/oauth2/default/v1/userinfo',
    end_session_endpoint: 'https://dev-39125344.okta.com/oauth2/default/v1/logout',
    jwks_uri: 'https://dev-39125344.okta.com/oauth2/default/v1/keys'
  }
}
```

**For complete Okta setup instructions, see [Okta Configuration Guide](./okta.md)**

#### Keycloak (Tested and Verified)
```yaml
oidc-security-service:
  enabled: true
  debug: true
  oidc-providers:
    - provider: "keycloak"
      display-name: "Keycloak"
      enabled: true
      roles-claim-path: "realm_access.roles"
      domains:
        - "example.com"
      audience: "structures-client"
      client-id: "structures-client"
      authority: "http://localhost:8888/auth/realms/test"
      redirect-uri: "http://localhost:9090/login"
      post-logout-redirect-uri: "http://localhost:9090"
      silent-redirect-uri: "http://localhost:9090/login/silent-renew"
      roles:
        - "admin"
```

**Frontend Configuration (structures-frontend-next):**
```typescript
// In OidcConfiguration.ts
keycloak: {
  client_id: 'structures-client',
  client_secret: '',
  authority: 'http://localhost:8888/auth/realms/test',
  redirect_uri: 'http://localhost:5173/login',
  post_logout_redirect_uri: 'http://localhost:5173',
  silent_redirect_uri: 'http://localhost:5173/login/silent-renew',
  loadUserInfo: true,
  publicClient: {
    isPublicClient: true,
    responseType: 'code',
    responseMode: 'query'
  },
  // No explicit metadata - uses automatic discovery
}
```

**Docker Compose Setup:**
```bash
# Start Keycloak with PostgreSQL and auto-bootstrap
docker compose -f docker-compose/compose.keycloak.yml up -d keycloak-db keycloak keycloak-setup

# Optionally wait for bootstrapper to complete
docker wait keycloak-setup
```

#### Auth0
```yaml
oidc-security-service:
  enabled: true
  debug: true
  oidc-providers:
    - provider: "auth0"
      display-name: "Auth0"
      enabled: true
      roles-claim-path: "https://your-namespace/roles"
      domains:
        - "your-domain.com"
      audience: "https://your-api-identifier"
      client-id: "your-client-id"
      authority: "https://your-tenant.auth0.com/"
      redirect-uri: "http://localhost:9090/login"
      post-logout-redirect-uri: "http://localhost:9090"
      silent-redirect-uri: "http://localhost:9090/login/silent-renew"
      roles:
        - "user"
```

#### Azure AD
```yaml
oidc-security-service:
  enabled: true
  debug: true
  oidc-providers:
    - provider: "azure"
      display-name: "Azure AD"
      enabled: true
      roles-claim-path: "roles"
      domains:
        - "your-domain.com"
      audience: "your-application-client-id"
      client-id: "your-application-client-id"
      authority: "https://login.microsoftonline.com/your-tenant-id/v2.0"
      redirect-uri: "http://localhost:9090/login"
      post-logout-redirect-uri: "http://localhost:9090"
      silent-redirect-uri: "http://localhost:9090/login/silent-renew"
      roles:
        - "user"
```

## Social Login Configuration

### Google OAuth2

#### 1. Google Cloud Console Setup

1. **Create a Google Cloud Project:**
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Create a new project or select an existing one

2. **Enable Google+ API:**
   - Go to "APIs & Services" > "Library"
   - Search for "Google+ API" and enable it

3. **Create OAuth 2.0 Credentials:**
   - Go to "APIs & Services" > "Credentials"
   - Click "Create Credentials" > "OAuth 2.0 Client IDs"
   - Choose "Web application"
   - Add authorized redirect URIs:
     - `http://localhost:5173/login` (development)
     - `https://your-domain.com/login` (production)

4. **Get Client Information:**
   - Copy the Client ID and Client Secret
   - Note the authorized redirect URIs

#### 2. Frontend Configuration

**Environment Variables:**
```bash
VITE_OIDC_GOOGLE_ENABLED=true
VITE_GOOGLE_CLIENT_ID=your-google-client-id
VITE_GOOGLE_AUTHORITY=https://accounts.google.com
VITE_GOOGLE_REDIRECT_URI=http://localhost:5173/login
VITE_GOOGLE_POST_LOGOUT_REDIRECT_URI=http://localhost:5173
VITE_GOOGLE_SILENT_REDIRECT_URI=http://localhost:5173/login/silent-renew
```

**Frontend Configuration:**
```typescript
google: {
  enabled: true,
  client_id: 'your-google-client-id',
  client_secret: '',
  authority: 'https://accounts.google.com',
  redirect_uri: 'http://localhost:5173/login',
  post_logout_redirect_uri: 'http://localhost:5173',
  silent_redirect_uri: 'http://localhost:5173/login/silent-renew',
  loadUserInfo: true,
  publicClient: {
    isPublicClient: true,
    responseType: 'code',
    responseMode: 'query'
  },
  metadata: {
    authorization_endpoint: 'https://accounts.google.com/o/oauth2/v2/auth',
    token_endpoint: 'https://oauth2.googleapis.com/token',
    userinfo_endpoint: 'https://openidconnect.googleapis.com/v1/userinfo',
    jwks_uri: 'https://www.googleapis.com/oauth2/v3/certs',
  },
}
```

#### 3. Backend Configuration

```yaml
oidc-security-service:
  enabled: true
  debug: true
  oidc-providers:
    - provider: "google"
      display-name: "Google"
      enabled: true
      roles-claim-path: "roles"
      domains:
        - "your-domain.com"
      audience: "your-google-client-id"
      client-id: "your-google-client-id"
      authority: "https://accounts.google.com"
      redirect-uri: "http://localhost:9090/login"
      post-logout-redirect-uri: "http://localhost:9090"
      silent-redirect-uri: "http://localhost:9090/login/silent-renew"
      roles:
        - "user"
```

### Microsoft Azure AD

#### 1. Azure Portal Setup

1. **Register an Application:**
   - Go to [Azure Portal](https://portal.azure.com/)
   - Navigate to "Azure Active Directory" > "App registrations"
   - Click "New registration"
   - Enter app name and redirect URI: `http://localhost:5173/login`

2. **Configure Authentication:**
   - Go to "Authentication" in your app registration
   - Add platform: "Single-page application (SPA)"
   - Add redirect URIs:
     - `http://localhost:5173/login`
     - `http://localhost:5173/login/silent-renew`

3. **Get Client Information:**
   - Copy the Application (client) ID
   - Note the Directory (tenant) ID

#### 2. Frontend Configuration

**Environment Variables:**
```bash
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=your-microsoft-client-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0
VITE_MICROSOFT_REDIRECT_URI=http://localhost:5173/login
VITE_MICROSOFT_POST_LOGOUT_REDIRECT_URI=http://localhost:5173
VITE_MICROSOFT_SILENT_REDIRECT_URI=http://localhost:5173/login/silent-renew
```

**Frontend Configuration:**
```typescript
microsoft: {
  enabled: true,
  client_id: 'your-microsoft-client-id',
  client_secret: '',
  authority: 'https://login.microsoftonline.com/your-tenant-id/v2.0',
  redirect_uri: 'http://localhost:5173/login',
  post_logout_redirect_uri: 'http://localhost:5173',
  silent_redirect_uri: 'http://localhost:5173/login/silent-renew',
  loadUserInfo: true,
  publicClient: {
    isPublicClient: true,
    responseType: 'code',
    responseMode: 'query'
  },
  metadata: {
    authorization_endpoint: 'https://login.microsoftonline.com/your-tenant-id/oauth2/v2.0/authorize',
    token_endpoint: 'https://login.microsoftonline.com/your-tenant-id/oauth2/v2.0/token',
    userinfo_endpoint: 'https://graph.microsoft.com/oidc/userinfo',
    end_session_endpoint: 'https://login.microsoftonline.com/your-tenant-id/oauth2/v2.0/logout',
    jwks_uri: 'https://login.microsoftonline.com/your-tenant-id/discovery/v2.0/keys',
  },
}
```

#### 3. Backend Configuration

```yaml
oidc-security-service:
  enabled: true
  debug: true
  oidc-providers:
    - provider: "microsoft"
      display-name: "Microsoft Azure AD"
      enabled: true
      roles-claim-path: "roles"
      domains:
        - "your-domain.com"
      audience: "your-microsoft-client-id"
      client-id: "your-microsoft-client-id"
      authority: "https://login.microsoftonline.com/your-tenant-id/v2.0"
      redirect-uri: "http://localhost:9090/login"
      post-logout-redirect-uri: "http://localhost:9090"
      silent-redirect-uri: "http://localhost:9090/login/silent-renew"
      roles:
        - "user"
```

### GitHub OAuth

#### 1. GitHub Developer Settings

1. **Create OAuth App:**
   - Go to [GitHub Developer Settings](https://github.com/settings/developers)
   - Click "New OAuth App"
   - Fill in the form:
     - Application name: "Your App Name"
     - Homepage URL: `http://localhost:5173`
     - Authorization callback URL: `http://localhost:5173/login`

2. **Get Client Information:**
   - Copy the Client ID and Client Secret
   - Note the callback URL

#### 2. Frontend Configuration

**Environment Variables:**
```bash
VITE_OIDC_GITHUB_ENABLED=true
VITE_GITHUB_CLIENT_ID=your-github-client-id
VITE_GITHUB_AUTHORITY=https://github.com
VITE_GITHUB_REDIRECT_URI=http://localhost:5173/login
VITE_GITHUB_POST_LOGOUT_REDIRECT_URI=http://localhost:5173
VITE_GITHUB_SILENT_REDIRECT_URI=http://localhost:5173/login/silent-renew
```

**Frontend Configuration:**
```typescript
github: {
  enabled: true,
  client_id: 'your-github-client-id',
  client_secret: '',
  authority: 'https://github.com',
  redirect_uri: 'http://localhost:5173/login',
  post_logout_redirect_uri: 'http://localhost:5173',
  silent_redirect_uri: 'http://localhost:5173/login/silent-renew',
  loadUserInfo: true,
  publicClient: {
    isPublicClient: true,
    responseType: 'code',
    responseMode: 'query'
  },
  metadata: {
    authorization_endpoint: 'https://github.com/login/oauth/authorize',
    token_endpoint: 'https://github.com/login/oauth/access_token',
    userinfo_endpoint: 'https://api.github.com/user',
  },
}
```

#### 3. Backend Configuration

```yaml
oidc-security-service:
  enabled: true
  debug: true
  oidc-providers:
    - provider: "github"
      display-name: "GitHub"
      enabled: true
      roles-claim-path: "roles"
      domains:
        - "your-domain.com"
      audience: "your-github-client-id"
      client-id: "your-github-client-id"
      authority: "https://github.com"
      redirect-uri: "http://localhost:9090/login"
      post-logout-redirect-uri: "http://localhost:9090"
      silent-redirect-uri: "http://localhost:9090/login/silent-renew"
      roles:
        - "user"
```

## Frontend State Management

### State Flow
1. **Login Initiation**: 
   - Create base64-encoded state with provider and referer info
   - Store in localStorage via oidc-client-ts library
   - Redirect to OIDC provider

2. **Callback Processing**:
   - Check localStorage for stored state
   - Determine which provider was used
   - Use correct provider for callback
   - Library handles state validation automatically

### State Structure
```typescript
const stateObj = {
  referer: string | null,    // Original destination
  provider: OidcProvider     // Provider used for login
};
```

### Provider Detection
The frontend automatically detects which provider was used by checking localStorage:
```typescript
const stateKey = `oidc.${state}`;
const storedState = localStorage.getItem(stateKey);
const sessionState = JSON.parse(storedState);
const stateObj = JSON.parse(atob(sessionState.data));
const provider = stateObj.provider;
```

## JWT Claims Mapping

The implementation extracts the following information from JWT claims:

| JWT Claim | Participant Field | Description |
|-----------|------------------|-------------|
| `sub` | `id` | Subject identifier |
| `email` | `metadata.email` | User email (**REQUIRED**) |
| `name` | `metadata.name` | Full name |
| `preferred_username` | `metadata.name` | Username (fallback) |
| `tenant_id` or `tid` | `tenantId` | Tenant identifier |
| `roles`, `role`, `groups`, `realm_access.roles` | `roles` | User roles |

### Email Claim Requirements

**⚠️ CRITICAL**: The `email` claim is **REQUIRED** for authentication to succeed. The system follows this fallback order:

1. **Primary**: Extract `email` claim from access token
2. **Fallback 1**: If no email claim, try `preferred_username` claim (must be valid email format)
3. **Fallback 2**: If no preferred_username, try `sub` claim (must be valid email format)
4. **Failure**: If none exist or none are valid emails, authentication fails

**Why This Matters:**
- Email is used for user identification and tenant routing
- Without email information, user sessions cannot be created
- This is a security requirement, not optional

**Supported Claims (in priority order):**
- **`email`**: Standard OIDC email claim
- **`preferred_username`**: Standard OIDC preferred username claim (often email format)
- **`sub`**: Standard OIDC subject claim (if it's an email format)
- **`upn`**: Microsoft-specific User Principal Name claim
- **`unique_name`**: Microsoft-specific legacy claim

**Provider-Specific Notes:**
- **Okta**: Must configure access token to include email claim. See [Okta Configuration Guide](./okta.md)
- **Microsoft**: Usually includes email in access tokens, supports upn and unique_name
- **Keycloak**: Configurable, but typically minimal access tokens
- **Google**: Minimal access tokens, profile data via UserInfo endpoint

### JSON Path Extraction

The OIDC service now supports extracting values from nested JWT claims using dot-separated JSON paths. This allows you to access deeply nested claim values like `realm_access.roles` or `app_metadata.tenant.id`.

**Configuration Example:**
```yaml
oidc-security-service:
  oidc-providers:
    - provider: "keycloak"
      roles-claim-path: "realm_access.roles"  # Extract from nested path
      # ... other configuration
```

**Supported Path Formats:**
- Simple: `"email"`, `"name"`
- Nested: `"realm_access.roles"`, `"groups.department"`
- Deep: `"user.profile.preferences.theme"`

**Example JWT Payload and Path Extraction:**
```json
{
  "sub": "user123",
  "email": "user@example.com",
  "realm_access": {
    "roles": ["admin", "user"]
  },
  "app_metadata": {
    "tenant": {
      "id": "tenant-123"
    }
  }
}
```

| Path | Extracted Value |
|------|-----------------|
| `"email"` | `"user@example.com"` |
| `"realm_access.roles"` | `["admin", "user"]` |
| `"app_metadata.tenant.id"` | `"tenant-123"` |

## Caching Strategy

### JWKS Key Cache
- **TTL**: 1 hour
- **Max Size**: 100 keys
- **Purpose**: Cache individual public keys by issuer and key ID

### Well-known Configuration Cache
- **TTL**: 24 hours
- **Max Size**: 10 configurations
- **Purpose**: Cache OIDC provider discovery documents

## Security Features

### 1. Token Validation
- Verifies JWT signature using public keys from JWKS
- Validates token expiration
- Checks issuer against allowed list
- Validates audience against allowed list (supports multiple audiences)

### 2. State Management
- Base64 encoding for state parameters
- localStorage persistence for state tracking
- Automatic CSRF protection via oidc-client-ts
- Provider detection from stored state

### 3. Error Handling
- Graceful handling of invalid tokens
- Comprehensive logging for debugging
- No sensitive information in error messages

### 4. Cache Security
- Automatic cache expiration
- Size limits to prevent memory issues
- Secure key storage

### 5. Frontend Security
- Secure token storage in cookies
- Automatic token refresh
- Proper logout and session cleanup

## Usage Examples

### Backend API Request
```bash
curl -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..." \
     https://your-api.com/endpoint
```

### Frontend Integration
```javascript
// Automatic OIDC login flow
const userManager = createUserManager('keycloak');
await userManager.signinRedirect();

// Handle callback
const user = await userManager.signinRedirectCallback();
await userState.handleOidcLogin(user);
```

### JavaScript Client
```javascript
// Set authorization header with JWT token
const headers = {
  'Authorization': `Bearer ${jwtToken}`,
  'Content-Type': 'application/json'
};

fetch('/api/endpoint', { headers })
  .then(response => response.json())
  .then(data => console.log(data));
```

## Testing

### Unit Tests
Run the test suite to validate the implementation:

```bash
# Run all OIDC tests
./gradlew test --tests "*Oidc*"

# Run specific test classes
./gradlew test --tests OidcAuthVerifierTest
./gradlew test --tests JwksServiceTest
```

### Integration Testing
For integration testing, you can:

1. Use a test OIDC provider (e.g., Okta test instance)
2. Generate test JWT tokens
3. Mock the JWKS service for controlled testing

### Test Configuration
```yaml
# Test configuration
oidc-security-service:
  enabled: true
  debug: true
  oidc-providers:
    - provider: "test"
      display-name: "Test Provider"
      enabled: true
      roles-claim-path: "roles"
      domains:
        - "test-domain.com"
      audience: "test-audience"
      client-id: "test-client-id"
      authority: "https://test-issuer.com"
      redirect-uri: "http://localhost:9090/login"
      post-logout-redirect-uri: "http://localhost:9090"
      silent-redirect-uri: "http://localhost:9090/login/silent-renew"
      roles:
        - "user"
```

### Frontend Testing
```bash
# Start the frontend development server
cd structures-frontend-next
npm run dev

# Test OIDC login flow
# 1. Navigate to /login
# 2. Click "Continue with Keycloak" or "Continue with Okta"
# 3. Complete authentication
# 4. Verify navigation to /applications
```

### Keycloak Local Testing
```bash
# Start Keycloak and bootstrap
docker compose -f docker-compose/compose.keycloak.yml up -d keycloak-db keycloak keycloak-setup

# Wait for bootstrap to complete
docker wait keycloak-setup

# Test with test user
# Username: testuser@example.com
# Password: password123
```

## Troubleshooting

### Common Issues

1. **"No authorization header found"**
   - Ensure the Authorization header is present
   - Check header name case sensitivity

2. **"Invalid issuer"**
   - Verify the issuer in your JWT token matches allowed issuers
   - Check issuer URL format

3. **"Invalid audience"**
   - Ensure the audience in your JWT token matches allowed audiences
   - Verify client ID configuration

4. **"JWT token verification failed"**
   - Check JWT token format
   - Verify token signature
   - Ensure token is not expired

5. **"Navigation not working after login"**
   - Check browser console for debug logs
   - Verify router configuration
   - Ensure CONTINUUM_UI is properly initialized

6. **"Authority mismatch on settings vs. signin state"**
   - Clear localStorage and try again
   - Check provider configuration
   - Verify automatic discovery is working

### Social Login Specific Issues

#### Google
- **"Invalid client"**: Check that redirect URI matches exactly in Google Console
- **"Access denied"**: Ensure Google+ API is enabled
- **"Invalid audience"**: Verify client ID in backend configuration

#### Microsoft
- **"AADSTS50011"**: Redirect URI mismatch - check Azure app registration
- **"AADSTS70002"**: Invalid client ID or secret
- **"AADSTS90002"**: Tenant not found - verify tenant ID

#### GitHub
- **"Bad verification code"**: Check client secret and redirect URI
- **"Application not found"**: Verify OAuth app configuration
- **"Invalid state"**: Check state parameter handling

### Debug Logging

Enable debug logging for troubleshooting:

```yaml
logging:
  level:
    org.mindignited.structures.internal.config: DEBUG
```

### Frontend Debugging
```javascript
// Check authentication state
console.log('Is authenticated:', userState.isAuthenticated());

// Check navigation
console.log('Current route:', router.currentRoute.value.path);

// Check stored state
console.log('Stored state:', localStorage.getItem('oidc.state.your-client-id'));
```

## Performance Considerations

1. **Cache Tuning**: Adjust cache TTL and size based on your OIDC provider
2. **Network Timeouts**: Configure appropriate timeouts for JWKS fetching
3. **Memory Usage**: Monitor cache memory usage in production
4. **Concurrent Requests**: The implementation is thread-safe and handles concurrent requests

## Security Best Practices

1. **HTTPS Only**: Always use HTTPS in production
2. **Token Validation**: Never skip token validation
3. **Audience Validation**: Always validate audience claims
4. **Issuer Validation**: Strictly validate issuer claims
5. **Token Expiration**: Ensure tokens have reasonable expiration times
6. **Key Rotation**: Monitor for key rotation events from your OIDC provider
7. **State Management**: Use proper state validation to prevent CSRF attacks

## Migration from Other Authentication

To migrate from other authentication methods:

1. Configure OIDC properties
2. Enable OIDC authentication
3. Update client applications to send JWT tokens
4. Test thoroughly in staging environment
5. Deploy to production

## Support

For issues or questions:
1. Check the troubleshooting section
2. Review application logs
3. Verify OIDC provider configuration
4. Test with a known valid JWT token
5. Check browser console for frontend issues 