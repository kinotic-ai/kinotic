-- GitHub social provider powering "Continue with GitHub" on org signup, org login,
-- and invite-accept.
--
-- The clientId is the kinotic-ai GitHub App's OAuth client id. The app must have
-- "Request user authorization (OAuth)" enabled and the "Email addresses: read-only"
-- account permission — the callback reads the verified primary email from
-- GET /user/emails because GitHub issues no id_token (OAuth 2.0 only, no OIDC
-- discovery; the authority below is the fixed OAuth site, informational only).
--
-- The GitHub App must list these callback URLs (GitHub Apps allow up to 10),
-- repeated per environment origin (production apiBaseUrl, http://localhost:9090,
-- http://localhost:5173):
--   <apiBaseUrl>/api/auth/org/signup/social/callback/github-platform
--   <apiBaseUrl>/api/auth/org/login/social/callback/github-platform
--   <apiBaseUrl>/api/auth/invite/oidc/callback/github-platform
--
-- The client secret resolves via secretNameRef github-platform — Azure Key Vault in
-- prod, KINOTIC_AKV_GITHUB_PLATFORM env var in dev.

INSERT INTO kinotic_org_signup_oidc_configuration (id, name, provider, clientId, secretNameRef, authority, enabled, created, updated) VALUES ('github-platform', 'GitHub', 'github', 'REPLACE_WITH_GITHUB_APP_CLIENT_ID', 'github-platform', 'https://github.com/login', true, '2026-08-01', '2026-08-01') WITH REFRESH;
