# Social Login Documentation

This folder contains documentation for configuring social login providers (Google, Microsoft, GitHub, etc.) for consumer-facing applications.

## Social Login vs Enterprise OIDC

### Social Login (Consumer)
- **Purpose**: Allow users to sign in with their personal social accounts
- **Audience**: General public, consumer applications
- **Configuration**: Uses social provider's OAuth2/OIDC endpoints
- **User Management**: Users manage their own accounts
- **Examples**: Google, Microsoft Personal, GitHub, Facebook

### Enterprise OIDC (Business)
- **Purpose**: Enterprise authentication with organizational control
- **Audience**: Employees, business applications
- **Configuration**: Uses organization's identity provider (Entra ID, Okta, etc.)
- **User Management**: IT department manages user accounts
- **Examples**: Microsoft Entra ID, Okta, Keycloak

## Documentation Index

### [Microsoft Social Login](./microsoft-social.md)
- **Purpose**: Configure Microsoft personal account login
- **Covers**: Personal Microsoft account OAuth2 setup, consumer audience
- **Use Case**: When users sign in with personal Microsoft accounts

### [Google Social Login](./google-social.md)
- **Purpose**: Configure Google OAuth2 login
- **Covers**: Google Cloud Console setup, OAuth2 configuration
- **Use Case**: When users sign in with Google accounts

### [GitHub Social Login](./github-social.md)
- **Purpose**: Configure GitHub OAuth login
- **Covers**: GitHub OAuth app setup, personal account authentication
- **Use Case**: When users sign in with GitHub accounts

## Key Differences

### Configuration Differences

| Aspect | Social Login | Enterprise OIDC |
|--------|-------------|-----------------|
| **Provider Setup** | Social platform (Google Console, GitHub Apps) | Identity provider (Entra ID, Okta) |
| **Audience** | Consumer applications | Business applications |
| **User Accounts** | Personal accounts | Organizational accounts |
| **Authentication** | OAuth2/OIDC with personal providers | OIDC with enterprise provider |
| **User Management** | Self-service | IT managed |
| **Scopes** | Basic profile access | Enterprise permissions |

### Technical Differences

#### Social Login (Microsoft Personal)
```yaml
# Frontend Configuration
VITE_MICROSOFT_SOCIAL_CLIENT_ID=your-consumer-app-id
VITE_MICROSOFT_SOCIAL_AUTHORITY=https://login.microsoftonline.com/consumers/v2.0

# Backend Configuration
structures:
  oidc-auth-verifier:
    allowed-issuers:
      - "https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0"
    authorization-audiences:
      - "your-consumer-app-id"
```

#### Enterprise OIDC (Microsoft Entra ID)
```yaml
# Frontend Configuration
VITE_MICROSOFT_CLIENT_ID=your-enterprise-app-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0

# Backend Configuration
structures:
  oidc-auth-verifier:
    allowed-issuers:
      - "https://sts.windows.net"
    authorization-audiences:
      - "00000003-0000-0000-c000-000000000000"
```

## Common Social Login Providers

### Microsoft Personal Accounts
- **Endpoint**: `https://login.microsoftonline.com/consumers/v2.0`
- **Issuer**: `https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0`
- **Audience**: Your consumer app client ID
- **Use Case**: Personal Microsoft account login

### Google Accounts
- **Endpoint**: `https://accounts.google.com`
- **Issuer**: `https://accounts.google.com`
- **Audience**: Your Google OAuth client ID
- **Use Case**: Personal Google account login

### GitHub Accounts
- **Endpoint**: `https://github.com`
- **Issuer**: `https://github.com`
- **Audience**: Your GitHub OAuth app client ID
- **Use Case**: Personal GitHub account login

## Implementation Guide

### Step 1: Choose Your Approach
1. **Social Login**: For consumer applications, personal accounts
2. **Enterprise OIDC**: For business applications, organizational accounts

### Step 2: Configure Provider
1. **Social**: Set up OAuth2 app in social platform
2. **Enterprise**: Configure app registration in identity provider

### Step 3: Update Application
1. **Frontend**: Configure OIDC client with correct endpoints
2. **Backend**: Update issuer and audience validation

### Step 4: Test Authentication
1. **Social**: Test with personal accounts
2. **Enterprise**: Test with organizational accounts

## Security Considerations

### Social Login Security
- **User Consent**: Users must consent to app permissions
- **Limited Scopes**: Only basic profile information
- **Personal Data**: Handle personal data according to privacy laws
- **Account Linking**: Consider how to link social accounts to your app

### Enterprise OIDC Security
- **Organizational Control**: IT manages user accounts
- **Advanced Permissions**: Can request enterprise scopes
- **Compliance**: Must meet organizational security requirements
- **Single Sign-On**: Integrates with enterprise SSO

## Example Configurations

### Social Login Setup
```bash
# Environment Variables
VITE_OIDC_MICROSOFT_SOCIAL_ENABLED=true
VITE_MICROSOFT_SOCIAL_CLIENT_ID=your-consumer-app-id
VITE_MICROSOFT_SOCIAL_AUTHORITY=https://login.microsoftonline.com/consumers/v2.0
VITE_MICROSOFT_SOCIAL_REDIRECT_URI=http://localhost:5173/login
```

### Enterprise OIDC Setup
```bash
# Environment Variables
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=your-enterprise-app-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0
VITE_MICROSOFT_REDIRECT_URI=http://localhost:5173/login
```

## Testing

### Social Login Testing
1. **Use Personal Accounts**: Test with personal Microsoft/Google/GitHub accounts
2. **Check Permissions**: Verify only requested scopes are granted
3. **Test Account Linking**: Ensure social accounts link to your app users

### Enterprise OIDC Testing
1. **Use Organizational Accounts**: Test with enterprise accounts
2. **Check Enterprise Permissions**: Verify enterprise scopes work correctly
3. **Test SSO Integration**: Ensure single sign-on works properly

## Related Documentation

- [Microsoft Entra ID (Enterprise)](../entra/) - Enterprise Microsoft authentication
- [Core OIDC Implementation](../../structures-core/OIDC_IMPLEMENTATION.md) - Underlying OIDC implementation
- [Frontend OIDC Configuration](../../structures-frontend-next/src/pages/login/OidcConfiguration.ts) - Frontend configuration

## Support

For social login issues:
1. Check provider-specific documentation in this folder
2. Verify OAuth2 app configuration in social platform
3. Test with personal accounts
4. Review OAuth2 scopes and permissions

For enterprise OIDC issues:
1. Check [Enterprise Documentation](../entra/)
2. Verify app registration in identity provider
3. Test with organizational accounts
4. Review enterprise permissions and policies 