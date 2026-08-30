
-- Seed the default system administrator (password: kinotic). SYSTEM scope = no organizationId/applicationId.
INSERT INTO kinotic_participant_identity (id, type, email, displayName, authType, enabled) VALUES ('00000000-0000-0000-0000-000000000001', 'USER', 'admin@kinotic.local', 'System Admin', 'LOCAL', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000001', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;


-- Seed the kinotic-test organization used by end-to-end and core package tests
INSERT INTO kinotic_organization (id, name, description) VALUES ('kinotic-test', 'kinotic-test', 'Organization used by kinotic end-to-end and core package tests') WITH REFRESH;

-- Seed the kinotic-test organization user (password: kinotic)
INSERT INTO kinotic_participant_identity (id, type, email, displayName, authType, organizationId, enabled) VALUES ('00000000-0000-0000-0000-000000000002', 'USER', 'kinotic@kinotic.local', 'Kinotic Test', 'LOCAL', 'kinotic-test', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000002', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;


-- The vm-manager's machine identity: SYSTEM scope = no organizationId/applicationId, which is
-- what lets it host its VmManager service in the system zone. It connects with the id below as
-- clientId and 'kinotic' as clientSecret; a deployed vm-manager gets a provisioned secret instead.
INSERT INTO kinotic_participant_identity (id, type, displayName, authType, purpose, enabled) VALUES ('00000000-0000-0000-0000-000000000011', 'MACHINE', 'Development VM Manager', 'CLIENT_CREDENTIALS', 'NODE_AGENT', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000011', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
