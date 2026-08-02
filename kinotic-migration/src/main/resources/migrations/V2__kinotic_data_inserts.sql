-- Kinotic-curated social IdP configurations powering the "Continue with X" buttons on
-- /api/login/providers and the org-signup flow.
--
-- The OAuth client secret for each row is resolved at OAuth2-build time via
-- SecretReferenceResolver — Azure Key Vault in prod (kinotic.domain.secretStorage.azure.vaultUrl)
-- or KINOTIC_AKV_<uppercased,sanitized-secretNameRef> env vars in dev. The secret name
-- here must match the AKV secret object name terraform creates.
--
-- audience is intentionally not set: for these social providers (Google, Microsoft Entra
-- /common) the OAuth2 code-for-token exchange already pins the resulting id_token to our
-- client_id, and the signature is verified against the IdP's JWKS. Validating aud against
-- a configured value would only be belt-and-suspenders here. Per-org OidcConfiguration
-- rows used for SSO can populate audience when the org admin uses a custom audience
-- identifier — the orchestrator + Vert.x validation kicks in automatically when the field
-- is non-blank.
--
-- Rows for OIDC-compliant providers (Google, Entra) need only authority — endpoints come
-- from the provider's discovery document and identity from the id_token. Providers without
-- OIDC support (GitHub) set everything explicitly: authorizationUri/tokenUri for the code
-- flow, userInfoUri for identity, userEmailsUri for a GitHub-style verified-email lookup,
-- and scopes as the space-delimited OAuth scope string.
--
-- Each IdP application registration must list these redirect URIs, per environment origin:
--   <apiBaseUrl>/api/auth/org/login/social/callback/<id>
--   <apiBaseUrl>/api/auth/org/signup/social/callback/<id>
--   <apiBaseUrl>/api/auth/invite/oidc/callback/<id>

INSERT INTO kinotic_org_signup_oidc_configuration (id, name, provider, clientId, secretNameRef, authority, enabled, created, updated) VALUES ('entra-platform', 'Microsoft', 'azure-ad', 'f24706cc-55ff-4d17-b72c-11ddfa87966a', 'entra-platform', 'https://login.microsoftonline.com/common/v2.0', true, '2026-05-05', '2026-05-05') WITH REFRESH;

INSERT INTO kinotic_org_signup_oidc_configuration (id, name, provider, clientId, secretNameRef, authority, enabled, created, updated) VALUES ('google-platform', 'Google', 'google', '1018531658131-komame5nk0m59fkp4836b4hrci0r538r.apps.googleusercontent.com', 'google-platform', 'https://accounts.google.com', true, '2026-05-05', '2026-05-05') WITH REFRESH;

-- github-platform is the kinotic-ai GitHub App's OAuth credential: the app must have a
-- client secret generated, the callback URLs above registered under "Identifying and
-- authorizing users", and the "Email addresses: read-only" account permission (the
-- userEmailsUri lookup reads it). Leave "Request user authorization (OAuth) during
-- installation" unchecked — it reroutes the post-install redirect from the Setup URL
-- (completeInstall flow) to the OAuth callback; the standalone authorize flow used here
-- doesn't need it. GitHub ignores the scope param for GitHub Apps (App permissions
-- govern access); the scopes below keep a classic OAuth App registration working with
-- the same row.

INSERT INTO kinotic_org_signup_oidc_configuration (id, name, provider, clientId, secretNameRef, authority, authorizationUri, tokenUri, userInfoUri, userEmailsUri, scopes, enabled, created, updated) VALUES ('github-platform', 'GitHub', 'github', 'Iv23liN1suytxICfhtOz', 'github-platform', 'https://github.com/login', 'https://github.com/login/oauth/authorize', 'https://github.com/login/oauth/access_token', 'https://api.github.com/user', 'https://api.github.com/user/emails', 'read:user user:email', true, '2026-08-01', '2026-08-01') WITH REFRESH;
