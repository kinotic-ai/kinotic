-- IamUser: primary-for-login flag for multi-org users (term-queried)
ALTER TABLE kinotic_iam_user ADD COLUMN primary BOOLEAN;

-- OidcConfiguration: client-secret reference and provisioning-mode fields
ALTER TABLE kinotic_oidc_configuration ADD COLUMN clientSecretRef KEYWORD NOT INDEXED;
ALTER TABLE kinotic_oidc_configuration ADD COLUMN provisioningMode KEYWORD;
