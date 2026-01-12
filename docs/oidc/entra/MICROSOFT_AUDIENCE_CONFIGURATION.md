# Microsoft OIDC Audience Configuration Guide

## Overview

The audience (aud) claim in JWT tokens is a critical security parameter that ensures tokens are intended for your specific application. For Microsoft Azure AD, the audience configuration must match between your frontend OIDC client and backend token validation.

**Important Note**: Microsoft Azure AD uses different URLs for authentication and token issuance:
- **Authentication**: `https://login.microsoftonline.com` (where users log in)
- **Token Issuer**: `https://sts.windows.net` (where tokens are actually issued)

## Audience Configuration Options

### Option 1: Use Microsoft Graph API Audience (Default/Standard)

Microsoft OIDC clients typically request tokens for **Microsoft Graph API** by default:

- **Graph API Audience**: `00000003-0000-0000-c000-000000000000`
- **Purpose**: Access to Microsoft Graph API for user information
- **Standard**: This is the most common audience for Microsoft OIDC tokens

**Backend Configuration:**
```yaml
structures:
  oidc-auth-verifier:
    enabled: true
    allowed-issuers:
      - "https://sts.windows.net"
    authorization-audiences:
      - "00000003-0000-0000-c000-000000000000"  # Microsoft Graph API
```

### Option 2: Use Your Application's Client ID as Audience

If you want to use your application's client ID as the audience, you need to configure the OIDC client to request tokens specifically for your application.

#### Frontend Configuration

**Update OIDC Configuration:**
```typescript
microsoft: {
  enabled: true,
  client_id: 'your-application-client-id',
  authority: 'https://login.microsoftonline.com/your-tenant-id/v2.0',
  // Add custom scope to request tokens for your app (Azure AD v2.0)
  scope: 'openid profile email your-application-client-id'
}
```

**Environment Variables:**
```bash
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=your-application-client-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0
VITE_MICROSOFT_RESOURCE=your-application-client-id  # Add this
```

**Note**: Azure AD v2.0 uses the `scope` parameter instead of the `resource` parameter. The scope includes your application's client ID to request tokens with your application as the audience.

#### Backend Configuration

```yaml
structures:
  oidc-auth-verifier:
    enabled: true
    allowed-issuers:
      - "https://sts.windows.net"
    authorization-audiences:
      - "your-application-client-id"  # Your app's client ID
```

#### JWT Token Structure (Option 2)

```json
{
  "aud": "your-application-client-id",  // Your app's client ID
  "iss": "https://sts.windows.net",
  "sub": "user-id",
  "exp": 1640995200,
  "tid": "your-tenant-id"
}
```

## Understanding Microsoft's Token Architecture

### Authentication vs Token Issuance

Microsoft Azure AD separates authentication from token issuance:

1. **Authentication Endpoint**: `https://login.microsoftonline.com`
   - Where users authenticate
   - Used by OIDC client for login flow
   - Handles user interaction

2. **Token Issuer**: `https://sts.windows.net`
   - Where JWT tokens are actually issued
   - Appears in the `iss` claim of JWT tokens
   - Used by backend for token validation

### Standard Microsoft OIDC Audience

Microsoft OIDC clients typically request tokens for **Microsoft Graph API** by default:

- **Graph API Audience**: `00000003-0000-0000-c000-000000000000`
- **Purpose**: Access to Microsoft Graph API for user information
- **Standard**: This is the most common audience for Microsoft OIDC tokens

### JWT Token Structure

```json
{
  "aud": "00000003-0000-0000-c000-000000000000",  // Microsoft Graph API
  "iss": "https://sts.windows.net",  // Note: Not login.microsoftonline.com
  "sub": "user-id",
  "exp": 1640995200,
  "tid": "your-tenant-id"
}
```

## Audience Configuration

### 1. Frontend Configuration (OIDC Client)

The frontend OIDC client uses the authentication endpoint for login, but the JWT token will have Microsoft Graph API as the audience.

**Environment Variables:**
```bash
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=your-application-client-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0
```

**OIDC Configuration:**
```typescript
microsoft: {
  enabled: true,
  client_id: 'your-application-client-id', // Used for authentication
  authority: 'https://login.microsoftonline.com/your-tenant-id/v2.0', // Authentication endpoint
  // ... other configuration
}
```

### 2. Backend Configuration (Token Validation)

The backend must be configured to accept the **Microsoft Graph API audience** from the JWT token.

**Application Properties:**
```yaml
structures:
  oidc-auth-verifier:
    enabled: true
    allowed-issuers:
      - "https://sts.windows.net"  # The actual issuer in JWT tokens
    authorization-audiences:
      - "00000003-0000-0000-c000-000000000000"  # Microsoft Graph API (standard)
      - "your-application-client-id"  # Your app (if configured)
```

## Step-by-Step Configuration

### Step 1: Get Your Azure AD Application Information

1. **Go to Azure Portal:**
   - Navigate to [Azure Portal](https://portal.azure.com/)
   - Go to "Azure Active Directory" > "App registrations"
   - Select your application

2. **Copy Application (Client) ID:**
   - This is your audience value
   - Example: `c81b2096-7781-4eb1-a2f6-42371330add6`

3. **Copy Directory (Tenant) ID:**
   - This is used in the authentication endpoint
   - Example: `12345678-1234-1234-1234-123456789012`

### Step 2: Configure Frontend Environment Variables

**Development Environment (.env.development):**
```bash
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=c81b2096-7781-4eb1-a2f6-42371330add6
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/12345678-1234-1234-1234-123456789012/v2.0
VITE_MICROSOFT_REDIRECT_URI=http://localhost:5173/login
VITE_MICROSOFT_POST_LOGOUT_REDIRECT_URI=http://localhost:5173
VITE_MICROSOFT_SILENT_REDIRECT_URI=http://localhost:5173/login/silent-renew
```

**Production Environment (.env.production):**
```bash
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=c81b2096-7781-4eb1-a2f6-42371330add6
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/12345678-1234-1234-1234-123456789012/v2.0
VITE_MICROSOFT_REDIRECT_URI=https://your-domain.com/login
VITE_MICROSOFT_POST_LOGOUT_REDIRECT_URI=https://your-domain.com
VITE_MICROSOFT_SILENT_REDIRECT_URI=https://your-domain.com/login/silent-renew
```

### Step 3: Configure Backend Application Properties

**Development Environment (application-dev.yml):**
```yaml
structures:
  oidc-auth-verifier:
    enabled: true
    allowed-issuers:
      - "https://sts.windows.net"  # The actual JWT issuer
    authorization-audiences:
      - "c81b2096-7781-4eb1-a2f6-42371330add6"
```

**Production Environment (application-prod.yml):**
```yaml
structures:
  oidc-auth-verifier:
    enabled: true
    allowed-issuers:
      - "https://sts.windows.net"  # The actual JWT issuer
    authorization-audiences:
      - "c81b2096-7781-4eb1-a2f6-42371330add6"
```

## Audience Validation Process

### 1. Token Request (Frontend)
When the frontend requests a token from Microsoft:
```
Request: GET /oauth2/v2.0/authorize
Parameters:
  client_id: c81b2096-7781-4eb1-a2f6-42371330add6
  scope: openid profile email
  response_type: code
```

### 2. Token Response (Microsoft)
Microsoft issues a JWT token with the audience claim:
```json
{
  "aud": "c81b2096-7781-4eb1-a2f6-42371330add6",
  "iss": "https://sts.windows.net",  // Note: Not login.microsoftonline.com
  "sub": "user-id",
  "exp": 1640995200
}
```

### 3. Token Validation (Backend)
The backend validates the token:
```java
// OidcAuthVerifier validates:
// 1. Issuer matches allowed-issuers (https://sts.windows.net)
// 2. Audience matches authorization-audiences
// 3. Token signature is valid
// 4. Token is not expired
```

## Common Audience Configuration Issues

### Issue 1: "Invalid issuer" Error
**Symptoms:**
- Backend logs show "Invalid issuer" error
- JWT token issuer is `https://sts.windows.net` but backend expects `https://login.microsoftonline.com`

**Solution:**
- Update `allowed-issuers` to include `https://sts.windows.net`
- The authentication endpoint and token issuer are different

### Issue 2: "Invalid audience" Error
**Symptoms:**
- Backend logs show "Invalid audience" error
- Frontend login succeeds but API calls fail

**Solution:**
- Ensure `authorization-audiences` in backend matches `client_id` in frontend
- Check for typos in the client ID
- Verify the audience is exactly the same (case-sensitive)

### Issue 3: Multiple Audiences
**Scenario:** Your application needs to accept tokens for multiple client IDs

**Backend Configuration:**
```yaml
structures:
  oidc-auth-verifier:
    enabled: true
    allowed-issuers:
      - "https://sts.windows.net"
    authorization-audiences:
      - "client-id-1"
      - "client-id-2"
      - "client-id-3"
```

## Testing Audience Configuration

### 1. Verify Frontend Configuration
```javascript
// Check browser console for OIDC configuration
console.log('Microsoft OIDC Config:', {
  client_id: import.meta.env.VITE_MICROSOFT_CLIENT_ID,
  authority: import.meta.env.VITE_MICROSOFT_AUTHORITY
});
```

### 2. Verify Backend Configuration
```bash
# Check application logs for OIDC configuration
grep "authorization-audiences" application.log
```

### 3. Test Token Validation
1. Complete Microsoft login flow
2. Check browser network tab for token request
3. Decode JWT token to verify audience claim
4. Verify backend accepts the token

### 4. JWT Token Inspection
```javascript
// Decode JWT token in browser console
const token = 'your-jwt-token';
const payload = JSON.parse(atob(token.split('.')[1]));
console.log('Token Audience:', payload.aud);
console.log('Token Issuer:', payload.iss);  // Should be https://sts.windows.net
```

## Security Best Practices

### 1. Issuer Validation
- Always validate the issuer claim
- Use `https://sts.windows.net` for Microsoft tokens
- Never skip issuer validation

### 2. Audience Validation
- Always validate the audience claim
- Never skip audience validation
- Use exact string matching (case-sensitive)

### 3. Multiple Audiences
- Only include necessary audiences
- Remove unused audience entries
- Regularly audit audience configuration

### 4. Environment Separation
- Use different client IDs for dev/staging/prod
- Never share client secrets between environments
- Use environment-specific configuration

## Troubleshooting Checklist

### Frontend Issues
- [ ] `VITE_MICROSOFT_CLIENT_ID` is set correctly
- [ ] `VITE_MICROSOFT_AUTHORITY` uses correct tenant ID
- [ ] Redirect URIs match Azure AD configuration
- [ ] Application is configured as SPA in Azure AD

### Backend Issues
- [ ] `authorization-audiences` matches frontend `client_id`
- [ ] `allowed-issuers` includes `https://sts.windows.net`
- [ ] OIDC authentication is enabled
- [ ] Application properties are loaded correctly

### Azure AD Issues
- [ ] Application is registered in Azure AD
- [ ] Authentication is configured for SPA
- [ ] Redirect URIs are configured correctly
- [ ] Application permissions are set appropriately

## Example Complete Configuration

### Frontend (.env.development)
```bash
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=c81b2096-7781-4eb1-a2f6-42371330add6
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/12345678-1234-1234-1234-123456789012/v2.0
VITE_MICROSOFT_REDIRECT_URI=http://localhost:5173/login
VITE_MICROSOFT_POST_LOGOUT_REDIRECT_URI=http://localhost:5173
VITE_MICROSOFT_SILENT_REDIRECT_URI=http://localhost:5173/login/silent-renew
```

### Backend (application-dev.yml)
```yaml
structures:
  oidc-auth-verifier:
    enabled: true
    allowed-issuers:
      - "https://sts.windows.net"  # The actual JWT issuer
    authorization-audiences:
      - "c81b2096-7781-4eb1-a2f6-42371330add6"
```

### Azure AD App Registration
- **Application (client) ID**: `c81b2096-7781-4eb1-a2f6-42371330add6`
- **Directory (tenant) ID**: `12345678-1234-1234-1234-123456789012`
- **Redirect URIs**: `http://localhost:5173/login`
- **Platform**: Single-page application (SPA)

## Key Differences Summary

| Component | Frontend Configuration | Backend Configuration | JWT Token |
|-----------|----------------------|----------------------|-----------|
| **Authentication** | `https://login.microsoftonline.com` | N/A | N/A |
| **Token Issuer** | N/A | `https://sts.windows.net` | `"iss": "https://sts.windows.net"` |
| **Audience** | `client_id` | `authorization-audiences` | `"aud": "client-id"` | 