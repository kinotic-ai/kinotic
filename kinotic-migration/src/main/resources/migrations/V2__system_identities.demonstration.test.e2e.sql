
-- Seed the default system administrator (password: kinotic): a system user, which signs in to the system
-- console.
INSERT INTO kinotic_participant_identity (id, type, email, displayName, enabled) VALUES ('00000000-0000-0000-0000-000000000001', 'SYSTEM_USER', 'admin@kinotic.local', 'System Admin', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000001', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;

-- The vm-manager's machine identity: SYSTEM scope = no organizationId/applicationId, which is
-- what lets it host its VmManager service in the system zone. It connects with the id below as
-- clientId and 'kinotic' as clientSecret; a deployed vm-manager gets a provisioned secret instead.
INSERT INTO kinotic_participant_identity (id, type, machineType, displayName, enabled) VALUES ('00000000-0000-0000-0000-000000000011', 'MACHINE', 'CLIENT', 'Development VM Manager', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000011', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
