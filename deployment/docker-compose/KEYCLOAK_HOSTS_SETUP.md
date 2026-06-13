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

Both kinotic-server (in a container) and your browser (on the host) need to reach
Keycloak at the **same** hostname so the OIDC `iss` claim matches what kinotic-server
discovered against, and the browser-facing redirect URL Keycloak generates resolves
correctly:

- **kinotic-server → Keycloak** uses `http://keycloak:8888/auth/realms/test`
  (compose service-network hostname).
- **Your browser → Keycloak** during the IdP roundtrip needs to land on
  `http://keycloak:8888/...` too — Keycloak issues the redirect URL using its
  configured `KC_HOSTNAME=keycloak`.

`127.0.0.1 keycloak` makes the host machine resolve `keycloak` → 127.0.0.1, so the
browser can reach the published Keycloak port (mapped at `127.0.0.1:8888`) using the
same hostname kinotic-server uses internally. The JWT's `iss` claim will then exactly
match the configured authority — `OAuth2AuthFactory.isIssuerValid` does a strict
match — and the auth flow completes cleanly.

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
2. **Check if Keycloak is running**: `curl -s http://keycloak:8888/auth/health/ready`
3. **Restart your browser** after changing hosts file (some browsers cache DNS)
4. **Flush DNS cache** if needed (`sudo dscacheutil -flushcache` on macOS)

If you get `OIDC issuer validation failed` in the kinotic-server logs after a successful
sign-in at Keycloak, double-check that `KC_HOSTNAME` in `compose.keycloak.yml` is
`keycloak` (not `localhost`) — that controls the JWT's `iss` claim.
