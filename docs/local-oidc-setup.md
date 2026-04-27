# Local OIDC setup

How to wire a platform OIDC provider (Google, Microsoft Entra, etc.) into your local
Kinotic environment so the **Continue with X** buttons on the login/signup pages actually
work. Local accounts (basic email/password) work without any of this — read this only when
you want to test the social-login path.

## What gets configured

A **platform** `OidcConfiguration` is shown to every user as a login button. They live on
`KinoticSystem.oidcConfigurationIds` — the *reference* from the system entity is what marks
them as platform-wide; there is no flag on the config itself. Per-org SSO configs use the
same shape but are referenced from `Organization.oidcConfigurationIds` instead and reached
via the email-first lookup flow (admin UI for managing them is not built yet).

`PlatformOidcBootstrap` reads `kinotic.oidc.platformProviders[]` from your config on
startup, looks up each provider's client secret from a file under `kinotic.oidc.secretsPath/<id>`,
stores it in `SecretStorageService`, and upserts the `OidcConfiguration` entity. Restart
re-applies — it's idempotent.

## Use the shared Entra app (recommended for the team)

Global terraform creates a `kinotic-platform` Entra app registration with these redirect
URIs already pre-registered:

Each environment needs **two** entries — `/api/login/callback/...` for the login flow and `/api/signup/callback/...` for the signup flow (they're separate handlers):

| URI | Used by |
|---|---|
| `https://portal.<domain>/api/login/callback/entra-platform` | Production AKS — login |
| `https://portal.<domain>/api/signup/callback/entra-platform` | Production AKS — signup |
| `http://localhost:9090/api/login/callback/entra-platform` | Bare local Java (default web port) — login |
| `http://localhost:9090/api/signup/callback/entra-platform` | Bare local Java — signup |
| `http://localhost:5173/api/login/callback/entra-platform` | Vite dev server — login |
| `http://localhost:5173/api/signup/callback/entra-platform` | Vite dev server — signup |
| `https://localhost/api/login/callback/entra-platform` | KinD with mkcert — login |
| `https://localhost/api/signup/callback/entra-platform` | KinD with mkcert — signup |

The configId is **`entra-platform`** — it shows up in the URL path, the helm values, and
the file name on disk.

### 1. Pull the secret

```bash
cd deployment/terraform/azure/global
terraform apply       # only needed if the password resource hasn't been created yet
SECRET=$(terraform output -raw kinotic_oidc_entra_platform_client_secret)
CLIENT_ID=$(terraform output -raw kinotic_oidc_client_id)
AUTHORITY=$(terraform output -raw kinotic_oidc_authority)
```

### 2. Drop the secret in the right place

#### Bare-local Java (`development` profile)

```bash
mkdir -p ~/.kinotic/dev-oidc-secrets
printf '%s' "$SECRET" > ~/.kinotic/dev-oidc-secrets/entra-platform
```

Edit `kinotic-server/src/main/resources/application-development.yml` — uncomment the
`platformProviders` block and fill in `clientId` + `authority`:

```yaml
kinotic:
  oidc:
    secretsPath: ${user.home}/.kinotic/dev-oidc-secrets
    platformProviders:
      - id: entra-platform
        name: Microsoft
        provider: azure-ad
        clientId: <paste $CLIENT_ID>
        authority: <paste $AUTHORITY>
```

Then restart kinotic-server. On boot you should see:

```
INFO  PlatformOidcBootstrap - Created platform OIDC config entra-platform (Microsoft)
INFO  PlatformOidcBootstrap - Linked platform OIDC config entra-platform to KinoticSystem
```

#### KinD

```bash
deployment/scripts/set-oidc-client-secret.sh entra-platform
# paste $SECRET when prompted

kubectl -n kinotic rollout restart deployment/kinotic-server
```

Then add the same `platformProviders[0]` block to
`deployment/kind/config/kinotic-server/values.yaml`. The next `terraform apply` (or
`./bin/dev-reload.sh`) re-renders the chart and picks it up.

#### Docker Compose

Add to `deployment/docker-compose/compose.kinotic-server.yml` under `environment:`:

```yaml
KINOTIC_OIDC_SECRETSPATH: /var/run/kinotic-oidc-secrets
KINOTIC_OIDC_PLATFORMPROVIDERS_0_ID: entra-platform
KINOTIC_OIDC_PLATFORMPROVIDERS_0_NAME: Microsoft
KINOTIC_OIDC_PLATFORMPROVIDERS_0_PROVIDER: azure-ad
KINOTIC_OIDC_PLATFORMPROVIDERS_0_CLIENTID: <paste $CLIENT_ID>
KINOTIC_OIDC_PLATFORMPROVIDERS_0_AUTHORITY: <paste $AUTHORITY>
```

Mount a host directory containing the secret file (gitignored):

```yaml
volumes:
  - ./.local-oidc-secrets:/var/run/kinotic-oidc-secrets:ro
```

```bash
mkdir -p deployment/docker-compose/.local-oidc-secrets
printf '%s' "$SECRET" > deployment/docker-compose/.local-oidc-secrets/entra-platform
echo '.local-oidc-secrets/' >> deployment/docker-compose/.gitignore
```

### 3. Verify

Open the login page. You should see a **Continue with Microsoft** button before the email
field. Click it, sign in with your Entra account, you should land at `/applications`.

If something goes wrong, check `kubectl logs -n kinotic deployment/kinotic-server`
(KinD/Azure) or the local console (bare). Common causes:

| Symptom | Cause |
|---|---|
| `Continue with Microsoft` button missing | `platformProviders` block typo, or secret file missing — bootstrap silently skips with a warning log line |
| `?error=state_mismatch` after IdP returns | The browser cookie domain doesn't match. Make sure you opened the login page from the same scheme/host as `kinotic.appBaseUrl`. |
| `redirect_uri_mismatch` from Entra | The URI you reached the app at doesn't match a registered redirect URI. Check the table at the top — Entra accepts only the four listed. |
| `?error=email_not_verified` | Your Entra user has `email_verified=false` in the id_token. Use a real account, not a guest with an unverified email. |

## Bring-your-own IdP (no Azure access)

If you don't have terraform access or want to test with a different IdP:

1. Register a new app at the IdP (Google Cloud Console, Auth0, Keycloak, etc.).
2. Set its redirect URI to one of the four listed above (substituting your own configId
   instead of `entra-platform` in the path) — make the configId stable, you'll use it
   throughout.
3. Create a client secret. Drop it at `~/.kinotic/dev-oidc-secrets/<your-config-id>`.
4. Add a `platformProviders` entry pointing at your IdP's authority and clientId. The
   `provider` value must match an `OidcProviderKind` enum key — `google`, `keycloak`,
   `auth0`, `oidc` (generic), etc.

The `oidc` value is the universal escape hatch — any standards-compliant OIDC issuer works
with it as long as `authority` resolves a `.well-known/openid-configuration`.

## How rotation works

- **Azure**: `az keyvault secret set --vault-name <vault> --name entra-platform --value '<new>'`. The Secrets Store CSI driver re-syncs within 2 minutes; restart kinotic-server to re-run `PlatformOidcBootstrap` against the new value.
- **KinD**: re-run `set-oidc-client-secret.sh` and `rollout restart`.
- **Bare local**: overwrite the file and restart.

There's no hot-reload for OIDC client secrets — restart picks them up. (The platform-wide
JWT signing keys *do* hot-reload via the file watcher, but those are auto-generated, not
operator-set.)

## Removing a provider

Delete the entry from `platformProviders[]` and restart. The bootstrap doesn't garbage-collect
the `OidcConfiguration` entity from Elasticsearch — disable it via the admin API
(temporarily) or accept that it stays around until Phase 6 admin UI lands.
