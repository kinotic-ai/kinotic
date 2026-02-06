# Runtime Configuration

The `structures-frontend-next` application now supports runtime configuration loading from JSON files instead of environment variables. This allows you to change configuration without rebuilding the application.

## Configuration File Locations

The application will look for configuration files in the following order, with optional local overrides applied on top:

1. Base: `/config/app-config.json` (preferred)
   - Local override (optional): `/config/app-config.override.json` or `/config/app-config.json.local`
2. Base: `/app-config.json` (fallback)
   - Local override (optional): `/app-config.override.json` or `/app-config.json.local`
3. If no base file exists, a local override (if present) will be merged onto defaults

## Configuration File Format

Create a JSON file with the following structure:

```json
{
  "oidc": {
    "okta": {
      "enabled": false,
      "client_id": "your-okta-client-id",
      "authority": "https://your-okta-domain.okta.com",
      "redirect_uri": "http://localhost:5173/login",
      "post_logout_redirect_uri": "http://localhost:5173",
      "silent_redirect_uri": "http://localhost:5173/login/silent-renew"
    },
    "keycloak": {
      "enabled": true,
      "client_id": "your-keycloak-client-id",
      "authority": "http://localhost:8080/realms/your-realm",
      "redirect_uri": "http://localhost:5173/login",
      "post_logout_redirect_uri": "http://localhost:5173",
      "silent_redirect_uri": "http://localhost:5173/login/silent-renew"
    },
    "google": {
      "enabled": false,
      "client_id": "your-google-client-id",
      "authority": "https://accounts.google.com",
      "redirect_uri": "http://localhost:5173/login",
      "post_logout_redirect_uri": "http://localhost:5173",
      "silent_redirect_uri": "http://localhost:5173/login/silent-renew"
    },
    "github": {
      "enabled": false,
      "client_id": "your-github-client-id",
      "authority": "https://github.com",
      "redirect_uri": "http://localhost:5173/login",
      "post_logout_redirect_uri": "http://localhost:5173",
      "silent_redirect_uri": "http://localhost:5173/login/silent-renew"
    },
    "microsoft": {
      "enabled": false,
      "client_id": "your-microsoft-client-id",
      "authority": "https://login.microsoftonline.com/common/v2.0",
      "redirect_uri": "http://localhost:5173/login",
      "post_logout_redirect_uri": "http://localhost:5173",
      "silent_redirect_uri": "http://localhost:5173/login/silent-renew",
      "resource": "your-microsoft-resource"
    },
    "microsoftSocial": {
      "enabled": false,
      "client_id": "your-microsoft-social-client-id",
      "authority": "https://login.microsoftonline.com/consumers/v2.0",
      "redirect_uri": "http://localhost:5173/login",
      "post_logout_redirect_uri": "http://localhost:5173",
      "silent_redirect_uri": "http://localhost:5173/login/silent-renew"
    },
    "custom": {
      "enabled": false,
      "client_id": "your-custom-client-id",
      "authority": "https://your-custom-authority.com",
      "redirect_uri": "http://localhost:5173/login",
      "post_logout_redirect_uri": "http://localhost:5173",
      "silent_redirect_uri": "http://localhost:5173/login/silent-renew",
      "metadata": {
        "authorization_endpoint": "https://your-custom-authority.com/oauth/authorize",
        "token_endpoint": "https://your-custom-authority.com/oauth/token",
        "userinfo_endpoint": "https://your-custom-authority.com/oauth/userinfo",
        "end_session_endpoint": "https://your-custom-authority.com/oauth/logout",
        "jwks_uri": "https://your-custom-authority.com/.well-known/jwks.json"
      }
    },
    "apple": {
      "enabled": false,
      "client_id": "your-apple-client-id",
      "authority": "https://appleid.apple.com",
      "redirect_uri": "http://localhost:5173/login",
      "post_logout_redirect_uri": "http://localhost:5173",
      "silent_redirect_uri": "http://localhost:5173/login/silent-renew"
    }
  },
  "basicAuth": {
    "enabled": true
  },
  "debug": false
}
```

## Configuration Options

### OIDC Providers

Each OIDC provider has the following configuration options:

- `enabled`: Boolean to enable/disable the provider
- `client_id`: The OAuth client ID from your identity provider
- `authority`: The authority URL for your identity provider
- `redirect_uri`: The redirect URI after successful authentication
- `post_logout_redirect_uri`: The redirect URI after logout
- `silent_redirect_uri`: The redirect URI for silent token renewal

### Microsoft-specific Options

For Microsoft providers, you can also specify:
- `resource`: Custom resource identifier for Microsoft v2.0 endpoints

### Custom Provider

For custom OIDC providers, you can specify:
- `metadata`: Complete OIDC metadata including endpoints

### Basic Authentication

- `enabled`: Boolean to enable/disable basic username/password authentication

### Debug Mode

- `debug`: Boolean to enable debug logging

## Setup Instructions

1. Copy the example configuration file:
   ```bash
   cp public/app-config.json.example public/app-config.json
   ```

2. Edit the configuration file with your settings:
   ```bash
   # Edit the configuration file
   nano public/app-config.json
   ```

3. Build the application:
   ```bash
   npm run build
   ```

4. Deploy the application with your configuration file in the web root.

## Development

During development, place files in `public/` and they will be served by Vite. You can optionally create a local-only override that should not be committed:

```bash
# Example: local override next to base file
cp public/app-config.json public/app-config.override.json

# Or use the .json.local suffix
cp public/app-config.json public/app-config.json.local
```

Git ignore example (add to your ignore rules):
```
public/app-config.override.json
public/app-config.json.local
config/app-config.override.json
config/app-config.json.local
```

## Production Deployment

For production deployment:

1. Place your configuration file in the web root of your server
2. Ensure the file is accessible at `/app-config.json` or `/config/app-config.json`
3. The application will load the configuration at runtime

## Fallback Behavior

If no configuration file is found, the application will use default settings:
- Keycloak enabled with default settings
- Basic authentication enabled
- Debug mode disabled

## Migration from Environment Variables

If you were previously using environment variables, you can migrate by:

1. Creating a configuration file with your current settings
2. Removing the environment variables from your build process
3. Deploying the configuration file with your application

The application will automatically detect and use the new configuration system.
