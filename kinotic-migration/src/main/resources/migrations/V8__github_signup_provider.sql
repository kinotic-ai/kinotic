-- GitHub as a Kinotic-curated provider for org signup and login. GitHub publishes no OIDC
-- discovery document and issues no id_token, so OidcFlowOrchestrator branches on
-- provider = 'github' to run its fixed OAuth2 endpoints and read the identity from the
-- GitHub REST API (GET /user + GET /user/emails). authority/audience are absent —
-- nothing is discovered or validated against them for this provider.
--
-- clientId is the OAuth client id of the Kinotic GitHub App (kinotic.github.appSlug); the
-- client secret is resolved via SecretReferenceResolver from secretNameRef — Azure Key
-- Vault in prod, KINOTIC_AKV_GITHUB_PLATFORM env var in dev.
--
-- The GitHub App registration must list both callback URLs:
--   <apiBaseUrl>/api/auth/org/signup/social/callback/github-platform
--   <apiBaseUrl>/api/auth/org/login/social/callback/github-platform
-- and grant the account permission "Email addresses: Read-only" so user/emails is
-- readable with user tokens (a classic OAuth App relies on the requested user:email
-- scope instead).

INSERT INTO kinotic_org_signup_oidc_configuration (id, name, provider, clientId, secretNameRef, enabled, created, updated) VALUES ('github-platform', 'GitHub', 'github', 'REPLACE_WITH_GITHUB_APP_CLIENT_ID', 'github-platform', true, '2026-08-01', '2026-08-01') WITH REFRESH;
