# Keycloak Hosts File Setup

## Quick Setup

**Before starting Keycloak, you MUST add this entry to your hosts file:**

```bash
# On macOS/Linux, edit /etc/hosts
# On Windows, edit C:\Windows\System32\drivers\etc\hosts

# Add this line:
127.0.0.1 keycloak
```

## Why This Is Required

The Keycloak configuration uses `keycloak` as the hostname in the authority URL:
- **Internal URL**: `http://keycloak:8888/auth/realms/test` (for container-to-container communication)
- **External URL**: `http://localhost:8888/auth/realms/test` (for browser access)

By adding `127.0.0.1 keycloak` to your hosts file, both URLs resolve to the same service, allowing:
1. The application to use the internal hostname
2. Your browser to access Keycloak via localhost
3. Consistent configuration across all components

## Verification

After adding the hosts file entry, verify it works:

```bash
# Check if the entry exists
cat /etc/hosts | grep keycloak

# Should show: 127.0.0.1 keycloak

# Test resolution
ping -c 1 keycloak

# Should resolve to 127.0.0.1
```

## Troubleshooting

If you get connection errors:
1. **Verify hosts file**: `cat /etc/hosts | grep keycloak`
2. **Check if Keycloak is running**: `curl -s http://localhost:8888/auth/health/ready`
3. **Restart your browser/application** after changing hosts file
4. **Flush DNS cache** if needed (varies by OS)

## Complete Documentation

For full Keycloak setup instructions, see: [Keycloak Setup Guide](../structures-auth/oidc-docs/README_KEYCLOAK_SETUP.md)
