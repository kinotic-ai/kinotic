# Microsoft OIDC: Using Your Application's Client ID as Audience

## Overview

This guide explains how to configure Microsoft OIDC to use your application's client ID as the audience (`aud` claim) in JWT tokens instead of the default Microsoft Graph API audience.

## Default vs Custom Audience

### Default Behavior (Microsoft Graph API)
- **Audience**: `00000003-0000-0000-c000-000000000000` (Microsoft Graph API)
- **Purpose**: Access to Microsoft Graph API for user information
- **Configuration**: Standard OIDC client configuration

### Custom Behavior (Your Application)
- **Audience**: Your application's client ID
- **Purpose**: Tokens specifically for your application
- **Configuration**: Requires additional OIDC parameters

## Configuration Steps

### Step 1: Frontend Configuration

#### Environment Variables
```bash
# .env.development or .env.production
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=your-application-client-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0
VITE_MICROSOFT_RESOURCE=your-application-client-id  # Add this line
VITE_MICROSOFT_REDIRECT_URI=http://localhost:5173/login
VITE_MICROSOFT_POST_LOGOUT_REDIRECT_URI=http://localhost:5173
VITE_MICROSOFT_SILENT_REDIRECT_URI=http://localhost:5173/login/silent-renew
```

#### OIDC Configuration
The configuration automatically adds the custom scope when `VITE_MICROSOFT_RESOURCE` is set:

```typescript
microsoft: {
  enabled: true,
  client_id: 'your-application-client-id',
  authority: 'https://login.microsoftonline.com/your-tenant-id/v2.0',
  // Automatically added when VITE_MICROSOFT_RESOURCE is set
  scope: 'openid profile email your-application-client-id'
}
```

**Note**: Azure AD v2.0 uses the `scope` parameter instead of the `resource` parameter. The scope includes your application's client ID to request tokens with your application as the audience.

### Step 2: Backend Configuration

#### Application Properties
```yaml
# application-dev.yml or application-prod.yml
structures:
  oidc-auth-verifier:
    enabled: true
    allowed-issuers:
      - "https://sts.windows.net"
    authorization-audiences:
      - "your-application-client-id"  # Your app's client ID
```

### Step 3: Azure AD App Registration

#### 1. Configure Your Application
1. Go to [Azure Portal](https://portal.azure.com/)
2. Navigate to "Azure Active Directory" > "App registrations"
3. Select your application

#### 2. Add API Permissions (Optional)
If you want to access Microsoft Graph API from your application:
1. Go to "API permissions"
2. Click "Add a permission"
3. Select "Microsoft Graph"
4. Choose appropriate permissions (e.g., "User.Read")
5. Click "Add permissions"

#### 3. Configure Authentication
1. Go to "Authentication"
2. Add platform: "Single-page application (SPA)"
3. Add redirect URIs:
   - `http://localhost:5173/login` (development)
   - `https://your-domain.com/login` (production)
4. Under "Advanced settings":
   - Set "Allow public client flows" to "Yes"
5. Save changes

## JWT Token Structure

With this configuration, your JWT tokens will have:

```json
{
  "aud": "your-application-client-id",  // Your app's client ID
  "iss": "https://sts.windows.net",
  "sub": "user-id",
  "exp": 1640995200,
  "tid": "your-tenant-id",
  "scp": "openid profile email"  // Scopes granted
}
```

## Testing the Configuration

### 1. Verify Environment Variables
```bash
# Check that all variables are set
echo $VITE_OIDC_MICROSOFT_ENABLED
echo $VITE_MICROSOFT_CLIENT_ID
echo $VITE_MICROSOFT_AUTHORITY
echo $VITE_MICROSOFT_RESOURCE
```

### 2. Test Login Flow
1. Start your frontend application
2. Navigate to the login page
3. Click "Continue with Microsoft"
4. Complete the Microsoft login flow
5. Check the JWT token audience

### 3. Inspect JWT Token
```javascript
// Decode JWT token in browser console
const token = 'your-jwt-token';
const payload = JSON.parse(atob(token.split('.')[1]));
console.log('Token Audience:', payload.aud);  // Should be your client ID
console.log('Token Issuer:', payload.iss);    // Should be https://sts.windows.net
```

## Troubleshooting

### Issue: Still Getting Graph API Audience
**Cause**: The resource parameter is not being sent correctly
**Solution**: 
1. Verify `VITE_MICROSOFT_RESOURCE` is set to your client ID
2. Check browser network tab for the authorization request
3. Ensure the `resource` parameter is included in the request

### Issue: "Invalid audience" Error
**Cause**: Backend configuration doesn't match frontend
**Solution**:
1. Ensure `authorization-audiences` includes your client ID
2. Check for typos in the client ID
3. Verify the client ID is exactly the same (case-sensitive)

### Issue: "Invalid resource" Error
**Cause**: Azure AD doesn't recognize your application as a resource
**Solution**:
1. Ensure your application is properly registered in Azure AD
2. Check that the client ID is correct
3. Verify the application has the necessary permissions

## Security Considerations

### 1. Resource Parameter Security
- The resource parameter determines the audience
- Only use your own application's client ID
- Never use arbitrary values

### 2. Audience Validation
- Always validate the audience claim
- Use exact string matching (case-sensitive)
- Log authentication events for monitoring

### 3. Token Security
- Validate token expiration
- Check token signature
- Verify issuer claims
- Monitor for suspicious activity

## Example Complete Configuration

### Frontend (.env.development)
```bash
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=c81b2096-7781-4eb1-a2f6-42371330add6
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/12345678-1234-1234-1234-123456789012/v2.0
VITE_MICROSOFT_RESOURCE=c81b2096-7781-4eb1-a2f6-42371330add6
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
      - "https://sts.windows.net"
    authorization-audiences:
      - "c81b2096-7781-4eb1-a2f6-42371330add6"
```

### Expected JWT Token
```json
{
  "aud": "c81b2096-7781-4eb1-a2f6-42371330add6",
  "iss": "https://sts.windows.net",
  "sub": "user-id",
  "exp": 1640995200,
  "tid": "12345678-1234-1234-1234-123456789012"
}
```

## Comparison: Graph API vs Custom Audience

| Aspect | Graph API Audience | Custom Audience |
|--------|-------------------|-----------------|
| **Audience** | `00000003-0000-0000-c000-000000000000` | Your client ID |
| **Configuration** | Standard OIDC | Requires resource parameter |
| **Use Case** | Access Graph API | Application-specific tokens |
| **Complexity** | Simple | Requires additional setup |
| **Security** | Standard Microsoft pattern | Custom application security | 