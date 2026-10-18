
-- Seed the default system administrator (password: kinotic): an account, and its membership of the
-- platform, which is what lets it sign in to the system console.
INSERT INTO kinotic_participant_identity (id, type, email, displayName, enabled) VALUES ('00000000-0000-0000-0000-000000000001', 'USER', 'admin@kinotic.local', 'System Admin', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000001', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_membership (id, identityId, enabled, created, updated) VALUES ('00000000-0000-0000-0000-000000000001:system', '00000000-0000-0000-0000-000000000001', true, '2026-01-01', '2026-01-01') WITH REFRESH;


-- Seed the kinotic-test organization used by end-to-end and core package tests
INSERT INTO kinotic_organization (id, name, description) VALUES ('kinotic-test', 'kinotic-test', 'Organization used by kinotic end-to-end and core package tests') WITH REFRESH;

-- Seed the kinotic-test organization's member (password: kinotic): an account and its membership
INSERT INTO kinotic_participant_identity (id, type, email, displayName, enabled) VALUES ('00000000-0000-0000-0000-000000000002', 'USER', 'kinotic@kinotic.local', 'Kinotic Test', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000002', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_membership (id, identityId, organizationId, enabled, created, updated) VALUES ('00000000-0000-0000-0000-000000000002:kinotic-test', '00000000-0000-0000-0000-000000000002', 'kinotic-test', true, '2026-01-01', '2026-01-01') WITH REFRESH;


-- The vm-manager's machine identity: SYSTEM scope = no organizationId/applicationId, which is
-- what lets it host its VmManager service in the system zone. It connects with the id below as
-- clientId and 'kinotic' as clientSecret; a deployed vm-manager gets a provisioned secret instead.
INSERT INTO kinotic_participant_identity (id, type, machineKind, displayName, authType, enabled) VALUES ('00000000-0000-0000-0000-000000000011', 'MACHINE', 'CLIENT', 'Development VM Manager', 'CLIENT_CREDENTIALS', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000011', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
