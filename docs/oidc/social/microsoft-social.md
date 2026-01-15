# Microsoft Social Login (Personal Accounts)

## Overview

This guide explains how to configure Microsoft social login for personal Microsoft accounts (Outlook.com, Hotmail, etc.) using the Microsoft Identity Platform for consumers.

**Key Difference**: This is for personal Microsoft accounts, not enterprise Entra ID accounts.

## Microsoft Identity Platform for Consumers

### Consumer Endpoints
- **Authority**: `https://login.microsoftonline.com/consumers/v2.0`
- **Issuer**: `https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0`
- **Audience**: Your consumer app client ID
- **Scopes**: `openid profile email`

### Consumer Tenant ID
Microsoft uses a special tenant ID for consumer accounts:
- **Consumer Tenant ID**: `9188040d-6c67-4c5b-b112-36a304b66dad`
- **Purpose**: Identifies Microsoft consumer accounts (Outlook.com, Hotmail, etc.)

## Setup Process

### Step 1: Register Your Application

#### 1. Go to Azure Portal
1. Navigate to [Azure Portal](https://portal.azure.com/)
2. Go to "Microsoft Entra ID" > "App registrations"
3. Click "New registration"

#### 2. Configure Application
1. **Name**: Enter your application name (e.g., "My App - Social Login")
2. **Supported account types**: Select "Personal Microsoft accounts only"
3. **Redirect URI**: 
   - Platform: "Single-page application (SPA)"
   - URI: `http://localhost:5173/login` (development)
   - URI: `https://your-domain.com/login` (production)
4. Click "Register"

#### 3. Get Application Information
1. **Application (client) ID**: Copy this for your configuration
2. **Directory (tenant) ID**: This will be the consumer tenant ID
3. **Object ID**: Note this for reference

### Step 2: Configure Authentication

#### 1. Authentication Settings
1. Go to "Authentication" in your app registration
2. Add platform: "Single-page application (SPA)"
3. Add redirect URIs:
   - `http://localhost:5173/login` (development)
   - `https://your-domain.com/login` (production)
4. Under "Advanced settings":
   - Set "Allow public client flows" to "Yes"
5. Save changes

#### 2. API Permissions (Optional)
1. Go to "API permissions"
2. Click "Add a permission"
3. Select "Microsoft Graph"
4. Choose "Delegated permissions"
5. Select scopes:
   - `openid` (always required)
   - `profile` (for user profile)
   - `email` (for email address)
6. Click "Add permissions"

### Step 3: Frontend Configuration

#### Environment Variables
```bash
# .env.development
VITE_OIDC_MICROSOFT_SOCIAL_ENABLED=true
VITE_MICROSOFT_SOCIAL_CLIENT_ID=your-consumer-app-client-id
VITE_MICROSOFT_SOCIAL_AUTHORITY=https://login.microsoftonline.com/consumers/v2.0
VITE_MICROSOFT_SOCIAL_REDIRECT_URI=http://localhost:5173/login
VITE_MICROSOFT_SOCIAL_POST_LOGOUT_REDIRECT_URI=http://localhost:5173
VITE_MICROSOFT_SOCIAL_SILENT_REDIRECT_URI=http://localhost:5173/login/silent-renew
```

#### OIDC Configuration
```typescript
// Add to your OIDC configuration
microsoftSocial: {
  enabled: env.VITE_OIDC_MICROSOFT_SOCIAL_ENABLED === 'true',
  client_id: env.VITE_MICROSOFT_SOCIAL_CLIENT_ID || '',
  client_secret: '',
  authority: env.VITE_MICROSOFT_SOCIAL_AUTHORITY || 'https://login.microsoftonline.com/consumers/v2.0',
  redirect_uri: env.VITE_MICROSOFT_SOCIAL_REDIRECT_URI || '',
  post_logout_redirect_uri: env.VITE_MICROSOFT_SOCIAL_POST_LOGOUT_REDIRECT_URI || '',
  silent_redirect_uri: env.VITE_MICROSOFT_SOCIAL_SILENT_REDIRECT_URI || '',
  loadUserInfo: true,
  scope: 'openid profile email',
  metadata: {
    authorization_endpoint: 'https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize',
    token_endpoint: 'https://login.microsoftonline.com/consumers/oauth2/v2.0/token',
    userinfo_endpoint: 'https://graph.microsoft.com/oidc/userinfo',
    end_session_endpoint: 'https://login.microsoftonline.com/consumers/oauth2/v2.0/logout',
    jwks_uri: 'https://login.microsoftonline.com/consumers/discovery/v2.0/keys',
  },
}
```

### Step 4: Backend Configuration

#### Application Properties
```yaml
# application-dev.yml
structures:
  oidc-auth-verifier:
    enabled: true
    allowed-issuers:
      - "https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0"
    authorization-audiences:
      - "your-consumer-app-client-id"
```

## JWT Token Structure

### Expected JWT Token
```json
{
  "aud": "your-consumer-app-client-id",
  "iss": "https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0",
  "sub": "user-id",
  "exp": 1640995200,
  "scp": "openid profile email",
  "tid": "9188040d-6c67-4c5b-b112-36a304b66dad"
}
```

### Key Differences from Enterprise
- **Issuer**: Uses consumer tenant ID instead of `sts.windows.net`
- **Audience**: Your app's client ID instead of Microsoft Graph API
- **Tenant ID**: Always `9188040d-6c67-4c5b-b112-36a304b66dad`

## Testing

### Step 1: Verify Configuration
```javascript
// Check JWT token in browser console
const token = 'your-jwt-token';
const payload = JSON.parse(atob(token.split('.')[1]));
console.log('Audience:', payload.aud);
console.log('Issuer:', payload.iss);
console.log('Tenant ID:', payload.tid);
```

### Step 2: Test Login Flow
1. **Start your application**
2. **Navigate to login page**
3. **Click "Continue with Microsoft"**
4. **Sign in with personal Microsoft account** (Outlook.com, Hotmail, etc.)
5. **Verify successful redirect**

### Step 3: Verify Token Validation
1. **Check backend logs** for successful JWT validation
2. **Verify user information** is extracted correctly
3. **Test logout flow**

## Common Issues

### Issue 1: "Invalid issuer" Error
**Cause**: Backend configured for enterprise issuer
**Solution**: Update `allowed-issuers` to include consumer issuer:
```yaml
allowed-issuers:
  - "https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0"
```

### Issue 2: "Invalid audience" Error
**Cause**: Backend expecting Microsoft Graph API audience
**Solution**: Update `authorization-audiences` to include your client ID:
```yaml
authorization-audiences:
  - "your-consumer-app-client-id"
```

### Issue 3: "Account type not supported" Error
**Cause**: App registered for organizational accounts only
**Solution**: Update app registration to support personal accounts:
1. Go to Azure Portal > App registrations > Your app
2. Go to "Authentication"
3. Change "Supported account types" to "Personal Microsoft accounts only"

### Issue 4: "Redirect URI mismatch" Error
**Cause**: Redirect URI not configured correctly
**Solution**: 
1. Verify redirect URI in Azure Portal matches your configuration
2. Check for exact string matching (including protocol and port)

## Security Considerations

### 1. Consumer Account Security
- **Personal Data**: Handle personal Microsoft account data carefully
- **Privacy Compliance**: Follow privacy laws (GDPR, CCPA, etc.)
- **User Consent**: Ensure users understand what data you're accessing
- **Data Minimization**: Only request necessary scopes

### 2. Token Security
- **Validate Issuer**: Always verify the consumer issuer
- **Validate Audience**: Ensure tokens are for your application
- **Check Expiration**: Validate token expiration
- **Monitor Usage**: Log authentication events

### 3. Account Linking
- **User Experience**: Consider how to link social accounts to your app users
- **Data Consistency**: Ensure consistent user experience across login methods
- **Account Recovery**: Plan for account recovery scenarios

## Example Complete Configuration

### Frontend (.env.development)
```bash
VITE_OIDC_MICROSOFT_SOCIAL_ENABLED=true
VITE_MICROSOFT_SOCIAL_CLIENT_ID=c81b2096-7781-4eb1-a2f6-42371330add6
VITE_MICROSOFT_SOCIAL_AUTHORITY=https://login.microsoftonline.com/consumers/v2.0
VITE_MICROSOFT_SOCIAL_REDIRECT_URI=http://localhost:5173/login
VITE_MICROSOFT_SOCIAL_POST_LOGOUT_REDIRECT_URI=http://localhost:5173
VITE_MICROSOFT_SOCIAL_SILENT_REDIRECT_URI=http://localhost:5173/login/silent-renew
```

### Backend (application-dev.yml)
```yaml
structures:
  oidc-auth-verifier:
    enabled: true
    allowed-issuers:
      - "https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0"
    authorization-audiences:
      - "c81b2096-7781-4eb1-a2f6-42371330add6"

logging:
  level:
    org.mindignited.structures.internal.config: DEBUG
    org.mindignited.structures.internal.security: DEBUG
```

### Azure Portal App Registration
- **Application (client) ID**: `c81b2096-7781-4eb1-a2f6-42371330add6`
- **Supported account types**: "Personal Microsoft accounts only"
- **Redirect URIs**: `http://localhost:5173/login`
- **Platform**: Single-page application (SPA)

## Comparison: Social vs Enterprise

| Aspect | Social Login | Enterprise OIDC |
|--------|-------------|-----------------|
| **Authority** | `consumers/v2.0` | `your-tenant-id/v2.0` |
| **Issuer** | Consumer tenant ID | `https://sts.windows.net` |
| **Audience** | Your client ID | Microsoft Graph API |
| **Accounts** | Personal Microsoft | Organizational |
| **User Management** | Self-service | IT managed |
| **Scopes** | Basic profile | Enterprise permissions |

## Troubleshooting

### Debug Logging
```yaml
logging:
  level:
    org.mindignited.structures.internal.config: DEBUG
    org.mindignited.structures.internal.security: DEBUG
```

### Test JWKS Endpoint
```bash
# Test Microsoft consumer JWKS endpoint
curl -s "https://login.microsoftonline.com/consumers/discovery/v2.0/keys" | jq
```

### Verify Token Structure
```javascript
// Decode JWT in browser console
const token = 'your-jwt-token';
const payload = JSON.parse(atob(token.split('.')[1]));
console.log('Token Structure:', payload);
```

## Related Documentation

- [Microsoft Entra ID (Enterprise)](../entra/) - Enterprise Microsoft authentication
- [Social Login Overview](./README.md) - General social login information
- [Core OIDC Implementation](../../structures-core/OIDC_IMPLEMENTATION.md) - Underlying OIDC implementation 