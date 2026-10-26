-- A demonstration server holds none of the secrets of the system's IdP registrations, and none of them lists its
-- callback URLs, so their buttons are turned off: people sign up and in with an email and a password, and an
-- application's users with the methods its own organization configures.
UPDATE kinotic_oidc_configuration SET enabled = false WHERE id = 'github-platform' WITH REFRESH ;
UPDATE kinotic_oidc_configuration SET enabled = false WHERE id = 'google-platform' WITH REFRESH ;
UPDATE kinotic_oidc_configuration SET enabled = false WHERE id = 'entra-platform' WITH REFRESH ;
