# Microsoft OIDC: AADSTS901002 Error Troubleshooting

## Error Description

```
AADSTS901002: The 'resource' request parameter is not supported.
```

## Root Cause

This error occurs because you're trying to use the `resource` parameter with Azure AD v2.0, which doesn't support this parameter. Azure AD v2.0 uses the `scope` parameter instead.

## Solution

### Option 1: Use Scope Parameter (Recommended)

**Update your OIDC configuration to use the scope parameter:**

```typescript
microsoft: {
  enabled: true,
  client_id: 'your-application-client-id',
  authority: 'https://login.microsoftonline.com/your-tenant-id/v2.0',
  // Use scope instead of resource parameter
  scope: 'openid profile email your-application-client-id'
}
```

**Environment Variables:**
```bash
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=your-application-client-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0
VITE_MICROSOFT_RESOURCE=your-application-client-id  # This will be used in scope
```

### Option 2: Use Default Graph API Audience

If you don't need a custom audience, use the default Microsoft Graph API audience:

**Frontend Configuration:**
```bash
# Remove VITE_MICROSOFT_RESOURCE or set it to empty
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=your-application-client-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0
# VITE_MICROSOFT_RESOURCE=  # Don't set this
```

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

## Azure AD v1.0 vs v2.0 Differences

### Azure AD v1.0 (Legacy)
- **Resource Parameter**: `resource=your-app-id`
- **Scope Parameter**: `scope=openid profile email`
- **Endpoint**: `https://login.microsoftonline.com/tenant-id/oauth2/authorize`

### Azure AD v2.0 (Current)
- **Resource Parameter**: Not supported
- **Scope Parameter**: `scope=openid profile email your-app-id`
- **Endpoint**: `https://login.microsoftonline.com/tenant-id/oauth2/v2.0/authorize`

## Implementation Steps

### Step 1: Update Frontend Configuration

1. **Set the environment variable:**
   ```bash
   VITE_MICROSOFT_RESOURCE=your-application-client-id
   ```

2. **The OIDC configuration will automatically use the scope parameter:**
   ```typescript
   // This is handled automatically by the configuration
   scope: 'openid profile email your-application-client-id'
   ```

### Step 2: Update Backend Configuration

```yaml
structures:
  oidc-auth-verifier:
    enabled: true
    allowed-issuers:
      - "https://sts.windows.net"
    authorization-audiences:
      - "your-application-client-id"
```

### Step 3: Test the Configuration

1. **Clear browser cache and cookies**
2. **Restart your frontend application**
3. **Test the Microsoft login flow**
4. **Check the JWT token audience**

## Expected JWT Token Structure

With the scope parameter configuration, your JWT tokens should have:

```json
{
  "aud": "your-application-client-id",
  "iss": "https://sts.windows.net",
  "sub": "user-id",
  "exp": 1640995200,
  "scp": "openid profile email your-application-client-id"
}
```

## Troubleshooting

### Issue: Still Getting AADSTS901002 Error
**Cause**: The resource parameter is still being sent
**Solution**: 
1. Ensure you're using the updated OIDC configuration
2. Clear browser cache and cookies
3. Check that `VITE_MICROSOFT_RESOURCE` is set correctly

### Issue: "Invalid scope" Error
**Cause**: The scope format is incorrect
**Solution**:
1. Ensure the scope includes `openid profile email`
2. Add your application's client ID to the scope
3. Check for typos in the client ID

### Issue: "Invalid audience" Error
**Cause**: Backend configuration doesn't match
**Solution**:
1. Update `authorization-audiences` to include your client ID
2. Restart your backend application
3. Test the authentication flow

## Alternative: Use Graph API Audience

If you prefer to use the standard Microsoft Graph API audience:

### Frontend Configuration
```bash
# Don't set VITE_MICROSOFT_RESOURCE
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=your-application-client-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0
```

### Backend Configuration
```yaml
structures:
  oidc-auth-verifier:
    enabled: true
    allowed-issuers:
      - "https://sts.windows.net"
    authorization-audiences:
      - "00000003-0000-0000-c000-000000000000"  # Microsoft Graph API
```

## Security Considerations

### 1. Scope Security
- Only include necessary scopes
- Always include `openid` for OIDC compliance
- Include `profile email` for user information
- Add your application's client ID for custom audience

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

### Expected OIDC Request
```
GET /oauth2/v2.0/authorize?
  client_id=c81b2096-7781-4eb1-a2f6-42371330add6&
  scope=openid%20profile%20email%20c81b2096-7781-4eb1-a2f6-42371330add6&
  response_type=code&
  redirect_uri=http://localhost:5173/login
``` 