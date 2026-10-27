-- The test-org organization, which the integration and e2e tests act in and the sample data (fixtures) fills.

INSERT INTO kinotic_organization (id, name, description) VALUES ('test-org', 'test-org', 'Organization used by kinotic end-to-end and core package tests') WITH REFRESH;

-- Seed the test-org organization's member (password: kinotic): a user of the organization
INSERT INTO kinotic_participant_identity (id, type, email, displayName, organizationId, enabled, created) VALUES ('00000000-0000-0000-0000-000000000002', 'ORGANIZATION_USER', 'kinotic@kinotic.local', 'Kinotic Test', 'test-org', true, '2026-01-01') WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000002', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;

-- test-org's login takes the member's password, as a PasswordConfiguration of the organization's own login
INSERT INTO kinotic_password_configuration (id, organizationId, enabled, createUserOnSignIn, created, updated) VALUES ('test-org', 'test-org', true, false, '2026-01-01', '2026-01-01') WITH REFRESH, ROUTING 'test-org', DOCUMENT_ID 'test-org--test-org';
