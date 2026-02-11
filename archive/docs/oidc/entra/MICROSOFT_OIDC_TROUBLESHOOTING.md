# Microsoft OIDC Troubleshooting Guide

## Common Error: AADSTS50194 - Multi-tenant Configuration

### Error Description
```
AADSTS50194: Application 'c81b2096-7781-4eb1-a2f6-42371330add6'(structures-ui-test) is not configured as a multi-tenant application. Usage of the /common endpoint is not supported for such applications created after '10/15/2018'. Use a tenant-specific endpoint or configure the application to be multi-tenant.
```

### Root Cause
The Microsoft OIDC configuration is using the `/common` endpoint, but the Azure AD application is configured as single-tenant (not multi-tenant).

### Solutions

#### Option 1: Use Tenant-Specific Endpoint (Recommended)

**Step 1: Get Your Tenant ID**
1. Go to [Azure Portal](https://portal.azure.com/)
2. Navigate to "Azure Active Directory" > "Overview"
3. Copy your "Tenant ID" (Directory ID)

**Step 2: Update Environment Variables**
```bash
# Replace 'your-tenant-id' with your actual tenant ID
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0
```

**Step 3: Update OIDC Configuration**
The configuration will automatically use the tenant-specific endpoint instead of `/common`.

#### Option 2: Configure Application as Multi-tenant

**Step 1: Update Azure AD App Registration**
1. Go to [Azure Portal](https://portal.azure.com/)
2. Navigate to "Azure Active Directory" > "App registrations"
3. Select your application
4. Go to "Authentication" in the left menu
5. Under "Advanced settings" > "Allow public client flows", set to "Yes"
6. Go to "Manifest" in the left menu
7. Find the `signInAudience` property and change it from `"AzureADMyOrg"` to `"AzureADandPersonalMicrosoftAccount"` or `"AzureADMultipleOrgs"`
8. Save the changes

**Step 2: Keep Current Configuration**
```bash
# Keep using the /common endpoint
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/common/v2.0
```

### Environment Variable Configuration

#### For Single-Tenant (Recommended)
```bash
# .env.development or .env.production
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=your-application-client-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0
VITE_MICROSOFT_REDIRECT_URI=http://localhost:5173/login
VITE_MICROSOFT_POST_LOGOUT_REDIRECT_URI=http://localhost:5173
VITE_MICROSOFT_SILENT_REDIRECT_URI=http://localhost:5173/login/silent-renew
```

#### For Multi-Tenant
```bash
# .env.development or .env.production
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=your-application-client-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/common/v2.0
VITE_MICROSOFT_REDIRECT_URI=http://localhost:5173/login
VITE_MICROSOFT_POST_LOGOUT_REDIRECT_URI=http://localhost:5173
VITE_MICROSOFT_SILENT_REDIRECT_URI=http://localhost:5173/login/silent-renew
```

### Azure AD App Registration Setup

#### 1. Create App Registration
1. Go to [Azure Portal](https://portal.azure.com/)
2. Navigate to "Azure Active Directory" > "App registrations"
3. Click "New registration"
4. Enter app name (e.g., "Structures UI")
5. Select supported account types:
   - **Single tenant**: "Accounts in this organizational directory only"
   - **Multi-tenant**: "Accounts in any organizational directory"
6. Set redirect URI: `http://localhost:5173/login`
7. Click "Register"

#### 2. Configure Authentication
1. Go to "Authentication" in your app registration
2. Add platform: "Single-page application (SPA)"
3. Add redirect URIs:
   - `http://localhost:5173/login`
   - `http://localhost:5173/login/silent-renew`
4. Under "Advanced settings":
   - Set "Allow public client flows" to "Yes"
5. Save changes

#### 3. Get Client Information
1. Copy the "Application (client) ID"
2. Note the "Directory (tenant) ID"
3. Use these values in your environment variables

### Testing the Configuration

#### 1. Verify Environment Variables
```bash
# Check that all required variables are set
echo $VITE_OIDC_MICROSOFT_ENABLED
echo $VITE_MICROSOFT_CLIENT_ID
echo $VITE_MICROSOFT_AUTHORITY
echo $VITE_MICROSOFT_REDIRECT_URI
```

#### 2. Test Login Flow
1. Start your frontend application
2. Navigate to the login page
3. Click "Continue with Microsoft"
4. Complete the Microsoft login flow
5. Verify successful redirect to your application

### Common Issues and Solutions

#### Issue: "Application not found"
**Solution**: Verify the client ID is correct in your environment variables

#### Issue: "Redirect URI mismatch"
**Solution**: Ensure the redirect URI in Azure AD matches exactly with your environment variable

#### Issue: "Invalid client"
**Solution**: Check that the application is properly configured and the client ID is correct

#### Issue: "AADSTS50011"
**Solution**: Verify the redirect URI in Azure AD matches your application's redirect URI

### Debug Information

#### Check Current Configuration
The application logs the OIDC configuration on startup:
```javascript
console.log('Creating UserManager with settings:', {
  authority: settings.authority,
  client_id: settings.client_id,
  redirect_uri: settings.redirect_uri,
  response_type: settings.response_type,
  response_mode: settings.response_mode
});
```

#### Browser Console Debugging
1. Open browser developer tools
2. Go to Console tab
3. Look for OIDC-related log messages
4. Check for any error messages during login

### Security Considerations

#### Single-Tenant vs Multi-Tenant
- **Single-Tenant**: More secure, only your organization's users can access
- **Multi-Tenant**: Allows users from other organizations to access (if configured)

#### Redirect URI Security
- Always use HTTPS in production
- Ensure redirect URIs are exact matches
- Avoid wildcard redirect URIs

### Production Deployment

#### Environment Variables for Production
```bash
# .env.production
VITE_OIDC_MICROSOFT_ENABLED=true
VITE_MICROSOFT_CLIENT_ID=your-production-client-id
VITE_MICROSOFT_AUTHORITY=https://login.microsoftonline.com/your-tenant-id/v2.0
VITE_MICROSOFT_REDIRECT_URI=https://your-domain.com/login
VITE_MICROSOFT_POST_LOGOUT_REDIRECT_URI=https://your-domain.com
VITE_MICROSOFT_SILENT_REDIRECT_URI=https://your-domain.com/login/silent-renew
```

#### Azure AD Production Configuration
1. Update redirect URIs in Azure AD to use your production domain
2. Ensure HTTPS is used for all redirect URIs
3. Test the complete authentication flow in production 