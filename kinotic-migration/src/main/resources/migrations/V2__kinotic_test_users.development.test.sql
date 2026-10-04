
-- Seed the default system administrator (password: kinotic). SYSTEM scope = no organizationId/applicationId.
INSERT INTO kinotic_participant_identity (id, type, email, displayName, authType, enabled) VALUES ('00000000-0000-0000-0000-000000000001', 'USER', 'admin@kinotic.local', 'System Admin', 'LOCAL', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000001', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;


-- Seed the kinotic-test organization used by end-to-end and core package tests; its creator is the
-- organization user seeded below, which the management server binds as the organization's administrator
INSERT INTO kinotic_organization (id, name, description, createdBy) VALUES ('kinotic-test', 'kinotic-test', 'Organization used by kinotic end-to-end and core package tests', '00000000-0000-0000-0000-000000000002') WITH REFRESH;

-- Seed the kinotic-test organization user (password: kinotic)
INSERT INTO kinotic_participant_identity (id, type, email, displayName, authType, organizationId, enabled) VALUES ('00000000-0000-0000-0000-000000000002', 'USER', 'kinotic@kinotic.local', 'Kinotic Test', 'LOCAL', 'kinotic-test', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000002', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;

-- Seed a second kinotic-test organization user (password: kinotic) that holds no grant until a test makes one,
-- so the end-to-end tests have a member whose access is exactly what they grant
INSERT INTO kinotic_participant_identity (id, type, email, displayName, authType, organizationId, enabled) VALUES ('00000000-0000-0000-0000-000000000004', 'USER', 'sally@kinotic.local', 'Sally', 'LOCAL', 'kinotic-test', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000004', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;


-- The vm-manager's machine identity: SYSTEM scope = no organizationId/applicationId, which is
-- what lets it host its VmManager service in the system zone. It connects with the id below as
-- clientId and 'kinotic' as clientSecret; a deployed vm-manager gets a provisioned secret instead.
INSERT INTO kinotic_participant_identity (id, type, machineKind, displayName, authType, enabled) VALUES ('00000000-0000-0000-0000-000000000011', 'MACHINE', 'CLIENT', 'Development VM Manager', 'CLIENT_CREDENTIALS', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000011', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
