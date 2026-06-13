-- Kinotic-curated social IdP configurations powering the "Continue with X" buttons on
-- /api/login/providers and the org-signup flow.
--
-- The OAuth client secret for each row is resolved at OAuth2-build time via
-- SecretReferenceResolver — Azure Key Vault in prod (kinotic.secretStorage.azure.vaultUrl)
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
-- Each IdP application registration must list these redirect URIs:
--   <apiBaseUrl>/api/login/callback/social/<id>   (org-login social path)
--   <apiBaseUrl>/api/signup/callback/<id>         (org-signup path)

INSERT INTO kinotic_org_signup_oidc_configuration (id, name, provider, clientId, secretNameRef, authority, enabled, created, updated) VALUES ('entra-platform', 'Microsoft', 'azure-ad', 'f24706cc-55ff-4d17-b72c-11ddfa87966a', 'entra-platform', 'https://login.microsoftonline.com/common/v2.0', true, '2026-05-05', '2026-05-05') WITH REFRESH;

INSERT INTO kinotic_org_signup_oidc_configuration (id, name, provider, clientId, secretNameRef, authority, enabled, created, updated) VALUES ('google-platform', 'Google', 'google', '1018531658131-komame5nk0m59fkp4836b4hrci0r538r.apps.googleusercontent.com', 'google-platform', 'https://accounts.google.com', true, '2026-05-05', '2026-05-05') WITH REFRESH;
