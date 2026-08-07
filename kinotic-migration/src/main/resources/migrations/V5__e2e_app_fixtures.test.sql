-- APPLICATION-scope users for the kinotic-js e2e suites that act through an application
-- client. One applicationId per suite: EntityDefinition ids derive from
-- organizationId.applicationId.entityName, so suites running in parallel need distinct
-- applicationIds to avoid colliding on the same entity name. Password for every user: kinotic.
-- Applies only when the migration app runs with the 'test' profile — the e2e compose stack
-- sets it; a local stack must add it to seed these fixtures.

INSERT INTO kinotic_participant_identity (id, type, email, displayName, authType, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000003', 'USER', 'app-e2e-datastream-kinotic@test.local', 'e2e DataStream App User', 'LOCAL', 'kinotic-test', 'e2e-datastream', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_participant_identity (id, type, email, displayName, authType, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000004', 'USER', 'app-e2e-admin-entity-kinotic@test.local', 'e2e AdminEntityService App User', 'LOCAL', 'kinotic-test', 'e2e-admin-entity', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_participant_identity (id, type, email, displayName, authType, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000005', 'USER', 'app-e2e-named-query-kinotic@test.local', 'e2e NamedQuery App User', 'LOCAL', 'kinotic-test', 'e2e-named-query', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_participant_identity (id, type, email, displayName, authType, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000006', 'USER', 'app-e2e-versioned-kinotic@test.local', 'e2e Versioned App User', 'LOCAL', 'kinotic-test', 'e2e-versioned', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_participant_identity (id, type, email, displayName, authType, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000007', 'USER', 'app-e2e-entity-service-kinotic@test.local', 'e2e EntityService App User', 'LOCAL', 'kinotic-test', 'e2e-entity-service', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_participant_identity (id, type, email, displayName, authType, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000008', 'USER', 'app-e2e-admin-named-query-kinotic@test.local', 'e2e AdminNamedQuery App User', 'LOCAL', 'kinotic-test', 'e2e-admin-named-query', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_participant_identity (id, type, email, displayName, authType, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000009', 'USER', 'app-e2e-mcp-kinotic@test.local', 'e2e Mcp App User', 'LOCAL', 'kinotic-test', 'e2e-mcp', 'kinotic', true) WITH REFRESH;

INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000003', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000004', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000005', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000006', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000007', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000008', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000009', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;

-- APPLICATION-scope machine identity for the machine connection e2e tests
-- (clientId: the identity id below, clientSecret: kinotic)
INSERT INTO kinotic_participant_identity (id, type, displayName, authType, organizationId, applicationId, enabled) VALUES ('00000000-0000-0000-0000-000000000010', 'MACHINE', 'e2e Test Machine', 'CLIENT_CREDENTIALS', 'kinotic-test', 'e2e-mcp', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000010', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
