-- Fixture applications and APPLICATION-scope users for the kinotic-js e2e suites that act
-- through an application client. One application per suite: EntityDefinition ids derive from
-- organizationId.applicationId.entityName, so suites running in parallel need distinct
-- applications to avoid colliding on the same entity name. Password for every user: kinotic.

INSERT INTO kinotic_application (id, organizationId, description) VALUES ('e2e-datastream', 'kinotic-test', 'e2e fixture application for the DataStream suite') WITH REFRESH;
INSERT INTO kinotic_application (id, organizationId, description) VALUES ('e2e-admin-entity', 'kinotic-test', 'e2e fixture application for the AdminEntityService suite') WITH REFRESH;
INSERT INTO kinotic_application (id, organizationId, description) VALUES ('e2e-named-query', 'kinotic-test', 'e2e fixture application for the NamedQuery suite') WITH REFRESH;
INSERT INTO kinotic_application (id, organizationId, description) VALUES ('e2e-versioned', 'kinotic-test', 'e2e fixture application for the Versioned suite') WITH REFRESH;

INSERT INTO kinotic_iam_user (id, email, displayName, authType, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000003', 'app-e2e-datastream-kinotic@test.local', 'e2e DataStream App User', 'LOCAL', 'kinotic-test', 'e2e-datastream', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_iam_user (id, email, displayName, authType, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000004', 'app-e2e-admin-entity-kinotic@test.local', 'e2e AdminEntityService App User', 'LOCAL', 'kinotic-test', 'e2e-admin-entity', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_iam_user (id, email, displayName, authType, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000005', 'app-e2e-named-query-kinotic@test.local', 'e2e NamedQuery App User', 'LOCAL', 'kinotic-test', 'e2e-named-query', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_iam_user (id, email, displayName, authType, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000006', 'app-e2e-versioned-kinotic@test.local', 'e2e Versioned App User', 'LOCAL', 'kinotic-test', 'e2e-versioned', 'kinotic', true) WITH REFRESH;

INSERT INTO kinotic_iam_credential (id, passwordHash) VALUES ('00000000-0000-0000-0000-000000000003', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_iam_credential (id, passwordHash) VALUES ('00000000-0000-0000-0000-000000000004', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_iam_credential (id, passwordHash) VALUES ('00000000-0000-0000-0000-000000000005', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_iam_credential (id, passwordHash) VALUES ('00000000-0000-0000-0000-000000000006', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
