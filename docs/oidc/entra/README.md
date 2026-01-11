# Microsoft Entra ID (Azure AD) OIDC Documentation

This folder contains comprehensive documentation for configuring and troubleshooting Microsoft Entra ID (formerly Azure AD) OpenID Connect authentication.

## Documentation Index

### Configuration Guides

#### [Microsoft Audience Configuration](./MICROSOFT_AUDIENCE_CONFIGURATION.md)
- **Purpose**: Complete guide for configuring Microsoft OIDC audience settings
- **Covers**: Frontend and backend configuration, JWT token structure, security best practices
- **Use Case**: Setting up Microsoft OIDC authentication with proper audience validation

#### [Microsoft Client ID as Audience](./MICROSOFT_CLIENT_ID_AUDIENCE.md)
- **Purpose**: Guide for using your application's client ID as the JWT audience
- **Covers**: Custom audience configuration, Azure AD API setup, scope parameters
- **Use Case**: When you need application-specific tokens instead of Microsoft Graph API tokens

### Troubleshooting Guides

#### [Microsoft OIDC Troubleshooting](./MICROSOFT_OIDC_TROUBLESHOOTING.md)
- **Purpose**: General troubleshooting for Microsoft OIDC authentication
- **Covers**: Common errors, configuration issues, setup problems
- **Use Case**: When experiencing general Microsoft OIDC authentication issues

#### [AADSTS901002 Error Troubleshooting](./MICROSOFT_AADSTS901002_TROUBLESHOOTING.md)
- **Purpose**: Specific troubleshooting for the "resource parameter not supported" error
- **Covers**: Azure AD v2.0 configuration, scope vs resource parameters
- **Use Case**: When getting "AADSTS901002: The 'resource' request parameter is not supported"

## Quick Start

### 1. Basic Configuration (Recommended)
For most applications, use the standard Microsoft Graph API audience:

**Frontend Configuration:**
```bash
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=your-application-client-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0
VITE_MICROSOFT_REDIRECT_URI=http://localhost:5173/login
```

**Backend Configuration:**
```yaml
oidc-security-service:
  enabled: true
  debug: true
  oidc-providers:
    - provider: "microsoft"
      display-name: "Microsoft Entra ID"
      enabled: true
      roles-claim-path: "roles"
      domains:
        - "your-domain.com"
      audience: "00000003-0000-0000-c000-000000000000"  # Microsoft Graph API
      client-id: "your-application-client-id"
      authority: "https://login.microsoftonline.com/your-tenant-id/v2.0"
      redirect-uri: "http://localhost:9090/login"
      post-logout-redirect-uri: "http://localhost:9090"
      silent-redirect-uri: "http://localhost:9090/login/silent-renew"
      roles:
        - "user"
```

### 2. Custom Audience Configuration
If you need your application's client ID as the audience:

**Frontend Configuration:**
```bash
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=your-application-client-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0
VITE_MICROSOFT_RESOURCE=your-application-client-id
```

**Backend Configuration:**
```yaml
oidc-security-service:
  enabled: true
  debug: true
  oidc-providers:
    - provider: "microsoft"
      display-name: "Microsoft Entra ID"
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

## Common Issues

### AADSTS901002 Error
- **Error**: "The 'resource' request parameter is not supported"
- **Solution**: Use scope parameter instead of resource parameter
- **Guide**: [AADSTS901002 Troubleshooting](./MICROSOFT_AADSTS901002_TROUBLESHOOTING.md)

### AADSTS650053 Error
- **Error**: "The application asked for scope that doesn't exist"
- **Solution**: Use Microsoft Graph API audience instead of custom scope
- **Guide**: [Audience Configuration](./MICROSOFT_AUDIENCE_CONFIGURATION.md)

### JWT Signature Validation
- **Error**: "JWT signature does not match locally computed signature"
- **Solution**: Verify issuer and audience configuration
- **Guide**: [Audience Configuration](./MICROSOFT_AUDIENCE_CONFIGURATION.md)

## Key Concepts

### Microsoft Entra ID Architecture
- **Authentication Endpoint**: `https://login.microsoftonline.com` (where users log in)
- **Token Issuer**: `https://sts.windows.net` (where tokens are issued)
- **JWKS Endpoint**: `https://login.microsoftonline.com/common/discovery/v2.0/keys`

### JWT Token Structure
```json
{
  "aud": "00000003-0000-0000-c000-000000000000",  // Microsoft Graph API
  "iss": "https://sts.windows.net",                // Token issuer
  "sub": "user-id",                               // User identifier
  "exp": 1640995200,                              // Expiration time
  "kid": "key-id-from-jwks"                       // Key identifier
}
```

### Audience Options
1. **Microsoft Graph API** (Recommended): `00000003-0000-0000-c000-000000000000`
2. **Custom Application**: Your application's client ID (requires API configuration)

## Security Best Practices

### 1. Audience Validation
- Always validate the audience claim
- Use exact string matching (case-sensitive)
- Log authentication events for monitoring

### 2. Issuer Validation
- Strictly validate issuer claims
- Use `https://sts.windows.net` for Microsoft tokens
- Never skip issuer validation

### 3. Token Security
- Validate token expiration
- Check token signature
- Verify issuer claims
- Monitor for suspicious activity

## Azure Portal Setup

### 1. Application Registration
1. Go to [Azure Portal](https://portal.azure.com/)
2. Navigate to "Microsoft Entra ID" > "App registrations"
3. Create new registration or select existing
4. Configure authentication settings

### 2. Authentication Configuration
1. Go to "Authentication" in your app registration
2. Add platform: "Single-page application (SPA)"
3. Add redirect URIs:
   - `http://localhost:5173/login` (development)
   - `https://your-domain.com/login` (production)
4. Set "Allow public client flows" to "Yes"

### 3. API Permissions (Optional)
1. Go to "API permissions"
2. Add Microsoft Graph permissions if needed
3. Grant admin consent if required

## Testing

### 1. Verify Configuration
```javascript
// Check JWT token in browser console
const token = 'your-jwt-token';
const payload = JSON.parse(atob(token.split('.')[1]));
console.log('Audience:', payload.aud);
console.log('Issuer:', payload.iss);
```

### 2. Test JWKS Endpoint
```bash
# Test Microsoft's JWKS endpoint
curl -s "https://login.microsoftonline.com/common/discovery/v2.0/keys" | jq
```

### 3. Verify Backend Logs
```yaml
logging:
  level:
    org.mindignited.structures.internal.config: DEBUG
    org.mindignited.structures.internal.security: DEBUG
```

## Related Documentation

- [OIDC Implementation Guide](../structures-core/OIDC_IMPLEMENTATION.md) - Core OIDC implementation details
- Basic Auth Configuration - See `structures-frontend-next/CONFIGURATION.md` (section "Basic Authentication")
- [Frontend OIDC Configuration](../structures-frontend-next/src/pages/login/OidcConfiguration.ts) - Frontend OIDC setup

## Support

For issues not covered in these guides:
1. Check the troubleshooting guides above
2. Review application logs with debug logging enabled
3. Verify Azure Portal configuration
4. Test with Microsoft's OIDC endpoints directly 