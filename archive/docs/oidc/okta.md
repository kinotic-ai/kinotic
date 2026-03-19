# Okta OIDC Configuration Guide

## Overview

This guide explains how to configure Okta OIDC authentication for the Structures project. **Important**: Okta access tokens MUST include an email claim for authentication to succeed.

## Critical Requirements

### Access Token Email Claim Requirement

**⚠️ CRITICAL**: Your Okta OIDC application MUST be configured to include the `email` claim in the access token. The Structures authentication system requires this claim to function properly.

**Authentication Flow:**
1. **Primary**: Look for `email` claim in access token
2. **Fallback 1**: If no email claim, try `preferred_username` claim (must be a valid email format)
3. **Fallback 2**: If no preferred_username, try `sub` claim (must be a valid email format)
4. **Failure**: If none exist or none are valid emails, authentication will fail

**Why This Matters:**
- The system uses email for user identification and tenant routing
- Without email information, user sessions cannot be created
- This is a security requirement, not optional

**Supported Claims (in priority order):**
- **`email`**: Standard OIDC email claim (recommended)
- **`preferred_username`**: Standard OIDC preferred username claim (often email format)
- **`sub`**: Standard OIDC subject claim (if it's an email format)
- **`upn`**: Microsoft-specific User Principal Name claim
- **`unique_name`**: Microsoft-specific legacy claim

## Okta Application Configuration

### Step 1: Create OIDC Application in Okta

1. **Log into Okta Admin Console**
2. **Navigate to Applications > Applications**
3. **Click "Create App Integration"**
4. **Select "OIDC - OpenID Connect"**
5. **Choose "Single-page application (SPA)"**

### Step 2: Configure Application Settings

#### Basic Information
- **App name**: `Structures Application` (or your preferred name)
- **Logo**: Optional
- **App type**: Single-page application (SPA)

#### Sign-in Method
- **Grant type**: Authorization Code
- **PKCE**: **REQUIRED** - Check "Proof Key for Code Exchange (PKCE)"
- **Redirect URIs**: 
  - Development: `http://localhost:5173/login`
  - Production: `https://your-domain.com/login`
- **Sign-out redirect URIs**:
  - Development: `http://localhost:5173`
  - Production: `https://your-domain.com`

### Step 3: Configure Token Claims (CRITICAL)

#### Access Token Claims
You MUST configure the following claims to be included in the access token:

1. **Go to "Sign On" tab**
2. **Click "Edit" in the "OpenID Connect ID Token" section**
3. **Under "Claims" add:**
   - `email` - **REQUIRED** - Include in access token
   - `name` - Optional but recommended
   - `preferred_username` - Optional but recommended
   - `groups` - Optional, for role-based access control

#### Claim Configuration Example
```
Claim Name: email
Claim Type: Expression
Value: user.email
Include in: Access Token (REQUIRED)

Claim Name: preferred_username
Claim Type: Expression
Value: user.preferredUsername
Include in: Access Token (RECOMMENDED)

Claim Name: groups
Claim Type: Expression
Value: appuser.groups
Include in: Access Token (OPTIONAL)
```

### Step 4: Configure Groups Scope and Claims (Optional)

#### Create Groups Scope
1. **Go to "Sign On" tab**
2. **Scroll down to "Scopes" section**
3. **Click "Add Scope"**
4. **Configure the scope:**
   - **Name**: `groups`
   - **Display Name**: `Groups`
   - **Description**: `Access to user group information`
   - **Consent**: Check "Consent required" if you want user approval

#### Configure Groups Claim with Regex Matching
1. **Go to "Sign On" tab**
2. **Click "Edit" in the "OpenID Connect ID Token" section**
3. **Under "Claims" add:**
   - **Claim Name**: `groups`
   - **Claim Type**: Expression
   - **Value**: `appuser.groups`
   - **Include in**: Access Token
   - **Set value type**: Array
   - **Group filter**: Check "Filter" and use regex pattern `.*`

#### Selective Groups Scope Usage
This configuration allows you to:
- **Request groups only when needed**: Use `scope: 'openid profile email groups'` for specific requests
- **Control group access**: Users can consent to group information sharing
- **Flexible implementation**: Different applications can request different levels of group access

**Example Scopes:**
```
Basic authentication: 'openid profile email'
With groups: 'openid profile email groups'
With custom claims: 'openid profile email groups custom_scope'
```

### Step 4: Assign Users and Groups

1. **Go to "Assignments" tab**
2. **Assign users or groups to the application**
3. **Ensure users have email addresses configured**

## Frontend Configuration

### Environment Variables
```bash
# .env.development
VITE_OIDC_OKTA_ENABLED=true
VITE_OKTA_CLIENT_ID=your-okta-client-id
VITE_OKTA_AUTHORITY=https://your-okta-domain.okta.com/oauth2/default
VITE_OKTA_REDIRECT_URI=http://localhost:5173/login
VITE_OKTA_POST_LOGOUT_REDIRECT_URI=http://localhost:5173
VITE_OKTA_SILENT_REDIRECT_URI=http://localhost:5173/login/silent-renew
```

### OIDC Configuration
```typescript
// In OidcConfiguration.ts
okta: {
  client_id: 'your-okta-client-id',
  client_secret: '', // Not needed for SPA
  authority: 'https://your-okta-domain.okta.com/oauth2/default',
  redirect_uri: 'http://localhost:5173/login',
  post_logout_redirect_uri: 'http://localhost:5173',
  silent_redirect_uri: 'http://localhost:5173/login/silent-renew',
  loadUserInfo: true,
  scope: 'openid profile email', // email scope is REQUIRED for email claim
  // Optional: Add 'groups' scope if you need group/role information
  // scope: 'openid profile email groups',
  publicClient: {
    isPublicClient: true,
    responseType: 'code',
    responseMode: 'query'
  },
  metadata: {
    authorization_endpoint: 'https://your-okta-domain.okta.com/oauth2/default/v1/authorize',
    token_endpoint: 'https://your-okta-domain.okta.com/oauth2/default/v1/token',
    userinfo_endpoint: 'https://your-okta-domain.okta.com/oauth2/default/v1/userinfo',
    end_session_endpoint: 'https://your-okta-domain.okta.com/oauth2/default/v1/logout',
    jwks_uri: 'https://your-okta-domain.okta.com/oauth2/default/v1/keys'
  }
}
```

### Dynamic Scope Configuration

For applications that need flexible group access, you can implement dynamic scope selection:

```typescript
// Function to get scope based on requirements
function getScope(includeGroups: boolean = false): string {
  const baseScope = 'openid profile email';
  return includeGroups ? `${baseScope} groups` : baseScope;
}

// Usage examples
const basicScope = getScope(); // 'openid profile email'
const groupsScope = getScope(true); // 'openid profile email groups'

// Apply to OIDC configuration
const userManagerSettings = {
  ...baseSettings,
  scope: getScope(needsGroupAccess)
};
```

## Backend Configuration

### Application Properties
```yaml
# application.yml or application-{profile}.yml
oidc-security-service:
  enabled: true
  debug: true
  oidc-providers:
    - provider: "okta"
      display-name: "Okta"
      authority: "https://your-okta-domain.okta.com/oauth2/default"
      audience: "your-okta-client-id"
      client-id: "your-okta-client-id"
      domains: ["your-domain.com"]  # Optional: restrict to specific email domains
      roles-claim-path: "groups"    # Optional: extract roles from groups claim
      enabled: true
      redirect-uri: "http://localhost:9090/login"
      post-logout-redirect-uri: "http://localhost:9090"
      silent-redirect-uri: "http://localhost:9090/login/silent-renew"
      roles:
        - "user"
```

## Testing Your Configuration

### Step 1: Verify Access Token Claims
1. **Login with Okta**
2. **Check browser developer tools > Application > Local Storage**
3. **Look for the access token**
4. **Decode the JWT at [jwt.io](https://jwt.io)**
5. **Verify at least one of these claims is present:**
   - `email` (recommended)
   - `preferred_username` (if it's an email format)
   - `sub` (if it's an email format)

### Step 2: Check Authentication Flow
1. **Login should succeed**
2. **User should be redirected to the application**
3. **Check backend logs for successful authentication**

### Step 3: Verify User Information
1. **Check that user email is correctly extracted**
2. **Verify tenant routing works (if using multi-tenancy)**
3. **Confirm roles are extracted (if configured)**

### Step 4: Test Groups Scope (Optional)
1. **Test basic authentication** with scope `openid profile email`
2. **Test groups access** with scope `openid profile email groups`
3. **Verify groups claim** in access token using JWT decoder
4. **Check backend logs** for role extraction
5. **Test selective scope usage** by requesting different scopes for different operations

## Troubleshooting

### Common Issues

#### Issue: "No email found in claims" Error
**Cause**: Access token doesn't contain any of the supported email claims
**Solution**: 
1. Check Okta application configuration
2. Ensure at least one of these claims is included in access token:
   - `email` (recommended)
   - `preferred_username` (if it's an email format)
   - `sub` (if it's an email format)
3. Verify claim mapping is correct
4. Check that the claim value is a valid email format

#### Issue: "Invalid issuer" Error
**Cause**: Issuer URL doesn't match configuration
**Solution**:
1. Check `authority` in frontend configuration
2. Verify `allowed-issuers` in backend configuration
3. Ensure URLs match exactly (including `/oauth2/default`)

#### Issue: "Invalid audience" Error
**Cause**: Client ID doesn't match audience configuration
**Solution**:
1. Check `client_id` in frontend configuration
2. Verify `authorization-audiences` in backend configuration
3. Ensure client ID matches exactly

#### Issue: Authentication Fails Silently
**Cause**: Missing or invalid email claim
**Solution**:
1. Check access token claims using JWT decoder
2. Verify email claim is present and valid
3. Check backend logs for specific error messages

#### Issue: Groups Not Appearing in Access Token
**Cause**: Groups scope or claim not properly configured
**Solution**:
1. Verify groups scope is created in Okta application
2. Check that groups claim is configured with regex filter `.*`
3. Ensure scope includes `groups` when requesting authentication
4. Verify user is assigned to groups in Okta
5. Check claim mapping uses `appuser.groups` value

#### Issue: Groups Claim is Empty Array
**Cause**: User not assigned to groups or filter too restrictive
**Solution**:
1. Assign user to groups in Okta Admin Console
2. Check group filter regex pattern (use `.*` for all groups)
3. Verify groups are active and enabled
4. Test with different users who have group assignments

### Debug Mode

Enable debug logging to troubleshoot issues:

```yaml
# Backend configuration
oidc-security-service:
  debug: true

# Frontend configuration
debug: true
```

## Security Considerations

### Required Scopes
- `openid`: Required for OIDC compliance
- `profile`: Recommended for user information
- `email`: **REQUIRED** for email claim

### Token Validation
- JWT signature verification using JWKS
- Issuer validation against allowed issuers
- Audience validation against configured audiences
- Expiration validation
- Email claim validation

### Best Practices
1. **Use HTTPS in production**
2. **Implement proper CORS configuration**
3. **Regular security audits of Okta configuration**
4. **Monitor authentication logs**
5. **Use least privilege principle for user assignments**

## Example Complete Configuration

### Okta Application Settings
```
App Name: Structures Application
App Type: Single-page application (SPA)
Grant Type: Authorization Code
PKCE: Enabled
Redirect URIs: http://localhost:5173/login, https://your-domain.com/login
Sign-out URIs: http://localhost:5173, https://your-domain.com
```

### Access Token Claims
```
email -> user.email (REQUIRED - recommended)
preferred_username -> user.preferredUsername (RECOMMENDED - fallback)
name -> user.displayName (OPTIONAL)
groups -> appuser.groups (OPTIONAL - for roles)
```

### Frontend Configuration
```typescript
okta: {
  enabled: true,
  client_id: '0oa1a2b3c4d5e6f7g8h9',
  authority: 'https://your-okta-domain.okta.com/oauth2/default',
  redirect_uri: 'http://localhost:5173/login',
  post_logout_redirect_uri: 'http://localhost:5173',
  silent_redirect_uri: 'http://localhost:5173/login/silent-renew',
  scope: 'openid profile email' // Add 'groups' when role information is needed
}

// For applications requiring group access:
oktaWithGroups: {
  ...okta,
  scope: 'openid profile email groups'
}
```

### Backend Configuration
```yaml
oidc-security-service:
  enabled: true
  debug: true
  oidc-providers:
    - provider: "okta"
      display-name: "Okta"
      authority: "https://your-okta-domain.okta.com/oauth2/default"
      audience: "0oa1a2b3c4d5e6f7g8h9"
      client-id: "0oa1a2b3c4d5e6f7g8h9"
      domains: ["your-domain.com"]
      roles-claim-path: "groups"
      enabled: true
      redirect-uri: "http://localhost:9090/login"
      post-logout-redirect-uri: "http://localhost:9090"
      silent-redirect-uri: "http://localhost:9090/login/silent-renew"
      roles:
        - "user"
```

## Support

If you encounter issues with Okta OIDC configuration:

1. **Check this documentation first**
2. **Verify access token claims using JWT decoder**
3. **Enable debug logging**
4. **Check Okta application configuration**
5. **Review backend authentication logs**

**Remember**: The email claim in the access token is **REQUIRED** for authentication to succeed.
