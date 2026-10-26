-- APPLICATION-scope users for the kinotic-js e2e suites that act through an application
-- client. One applicationId per suite: EntityDefinition ids derive from
-- organizationId.applicationId.entityName, so suites running in parallel need distinct
-- applicationIds to avoid colliding on the same entity name. Password for every user: kinotic.
-- Applies only when the migration app runs with the 'test' profile — the e2e compose stack
-- sets it; a local stack must add it to seed these fixtures.

INSERT INTO kinotic_participant_identity (id, type, email, displayName, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000003', 'APPLICATION_USER', 'app-e2e-datastream-kinotic@test.local', 'e2e DataStream App User', 'test-org', 'e2e-datastream', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_participant_identity (id, type, email, displayName, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000005', 'APPLICATION_USER', 'app-e2e-named-query-kinotic@test.local', 'e2e NamedQuery App User', 'test-org', 'e2e-named-query', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_participant_identity (id, type, email, displayName, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000006', 'APPLICATION_USER', 'app-e2e-versioned-kinotic@test.local', 'e2e Versioned App User', 'test-org', 'e2e-versioned', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_participant_identity (id, type, email, displayName, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000007', 'APPLICATION_USER', 'app-e2e-entity-service-kinotic@test.local', 'e2e EntityService App User', 'test-org', 'e2e-entity-service', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_participant_identity (id, type, email, displayName, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000009', 'APPLICATION_USER', 'app-e2e-mcp-kinotic@test.local', 'e2e Mcp App User', 'test-org', 'e2e-mcp', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_participant_identity (id, type, email, displayName, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000014', 'APPLICATION_USER', 'app-e2e-migration-kinotic@test.local', 'e2e Migration App User', 'test-org', 'e2e-migration', 'kinotic', true) WITH REFRESH;
INSERT INTO kinotic_participant_identity (id, type, email, displayName, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000015', 'APPLICATION_USER', 'app-e2e-admin-named-query-tenant01@test.local', 'e2e AdminNamedQuery App User', 'test-org', 'e2e-admin-named-query', 'tenant01', true) WITH REFRESH;

INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000003', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000005', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000006', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000007', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000009', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000014', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000015', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;

-- The applications above take their users' passwords, each through a PasswordConfiguration of the application's own
-- login, which their tenants' logins take as well
INSERT INTO kinotic_password_configuration (id, organizationId, applicationId, enabled, createUserOnSignIn, created, updated) VALUES ('test-org:e2e-datastream', 'test-org', 'e2e-datastream', true, false, '2026-01-01', '2026-01-01') WITH REFRESH, ROUTING 'test-org', DOCUMENT_ID 'test-org--test-org:e2e-datastream';
INSERT INTO kinotic_password_configuration (id, organizationId, applicationId, enabled, createUserOnSignIn, created, updated) VALUES ('test-org:e2e-named-query', 'test-org', 'e2e-named-query', true, false, '2026-01-01', '2026-01-01') WITH REFRESH, ROUTING 'test-org', DOCUMENT_ID 'test-org--test-org:e2e-named-query';
INSERT INTO kinotic_password_configuration (id, organizationId, applicationId, enabled, createUserOnSignIn, created, updated) VALUES ('test-org:e2e-versioned', 'test-org', 'e2e-versioned', true, false, '2026-01-01', '2026-01-01') WITH REFRESH, ROUTING 'test-org', DOCUMENT_ID 'test-org--test-org:e2e-versioned';
INSERT INTO kinotic_password_configuration (id, organizationId, applicationId, enabled, createUserOnSignIn, created, updated) VALUES ('test-org:e2e-entity-service', 'test-org', 'e2e-entity-service', true, false, '2026-01-01', '2026-01-01') WITH REFRESH, ROUTING 'test-org', DOCUMENT_ID 'test-org--test-org:e2e-entity-service';
INSERT INTO kinotic_password_configuration (id, organizationId, applicationId, enabled, createUserOnSignIn, created, updated) VALUES ('test-org:e2e-mcp', 'test-org', 'e2e-mcp', true, false, '2026-01-01', '2026-01-01') WITH REFRESH, ROUTING 'test-org', DOCUMENT_ID 'test-org--test-org:e2e-mcp';
INSERT INTO kinotic_password_configuration (id, organizationId, applicationId, enabled, createUserOnSignIn, created, updated) VALUES ('test-org:e2e-migration', 'test-org', 'e2e-migration', true, false, '2026-01-01', '2026-01-01') WITH REFRESH, ROUTING 'test-org', DOCUMENT_ID 'test-org--test-org:e2e-migration';
INSERT INTO kinotic_password_configuration (id, organizationId, applicationId, enabled, createUserOnSignIn, created, updated) VALUES ('test-org:e2e-admin-named-query', 'test-org', 'e2e-admin-named-query', true, false, '2026-01-01', '2026-01-01') WITH REFRESH, ROUTING 'test-org', DOCUMENT_ID 'test-org--test-org:e2e-admin-named-query';

-- APPLICATION-scope machine identity for the machine connection e2e tests
-- (clientId: the identity id below, clientSecret: kinotic)
INSERT INTO kinotic_participant_identity (id, type, machineType, displayName, organizationId, applicationId, enabled) VALUES ('00000000-0000-0000-0000-000000000010', 'MACHINE', 'CLIENT', 'e2e Test Machine', 'test-org', 'e2e-mcp', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000010', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;

-- ORGANIZATION-scope machine identity, the shape a project's deployment provisions for its
-- sync workload: it names an organization but no application, so it acts for the whole
-- organization rather than as an end-user of one application. (clientSecret: kinotic)
INSERT INTO kinotic_participant_identity (id, type, machineType, displayName, organizationId, enabled) VALUES ('00000000-0000-0000-0000-000000000012', 'MACHINE', 'PROJECT_SYNC', 'e2e Org Machine', 'test-org', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000012', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;

-- ORGANIZATION-scope machine identity, the shape a project's deployment provisions for a
-- microservice's runtime workload: it connects to the app server and hosts and calls services in
-- the organization's application zones, as the node-failure suite's probe does. (clientSecret: kinotic)
INSERT INTO kinotic_participant_identity (id, type, machineType, displayName, organizationId, enabled) VALUES ('00000000-0000-0000-0000-000000000013', 'MACHINE', 'APP_RUNTIME', 'e2e Runtime Machine', 'test-org', true) WITH REFRESH;
INSERT INTO kinotic_identity_credential (id, secretHash) VALUES ('00000000-0000-0000-0000-000000000013', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;

-- The tenants the app users above belong to, so a sign-in addressed to the tenant finds them
INSERT INTO kinotic_tenant (id, organizationId, applicationId, name, description) VALUES ('kinotic', 'test-org', 'e2e-mcp', 'kinotic', 'Tenant of the e2e Mcp application') WITH REFRESH, ROUTING 'test-org', DOCUMENT_ID 'test-org--kinotic';
INSERT INTO kinotic_tenant (id, organizationId, applicationId, name, description) VALUES ('tenant01', 'test-org', 'e2e-admin-named-query', 'tenant01', 'Tenant of the e2e AdminNamedQuery application') WITH REFRESH, ROUTING 'test-org', DOCUMENT_ID 'test-org--tenant01';

-- A second organization whose login takes passwords, with a member under the e2e organization user's email that holds
-- no password: a user of each organization is its own, so the test-org member's password does not sign in here
INSERT INTO kinotic_organization (id, name, description) VALUES ('e2e-second', 'e2e-second', 'Second organization, with a member under the email of the e2e organization user') WITH REFRESH;
INSERT INTO kinotic_participant_identity (id, type, email, displayName, organizationId, enabled, created) VALUES ('00000000-0000-0000-0000-000000000016', 'ORGANIZATION_USER', 'kinotic@kinotic.local', 'Kinotic Test', 'e2e-second', true, '2026-01-01') WITH REFRESH;
INSERT INTO kinotic_password_configuration (id, organizationId, enabled, createUserOnSignIn, created, updated) VALUES ('e2e-second', 'e2e-second', true, false, '2026-01-01', '2026-01-01') WITH REFRESH, ROUTING 'e2e-second', DOCUMENT_ID 'e2e-second--e2e-second';

-- An organization whose login takes no password
INSERT INTO kinotic_organization (id, name, description) VALUES ('e2e-sso-only', 'e2e-sso-only', 'Organization whose login takes no password') WITH REFRESH;

-- The e2e organization's own Okta configuration, so a member may be added without an invitation: such a member signs in
-- through it, and nothing in the stack ever reaches the host it names
INSERT INTO kinotic_oidc_configuration (id, type, organizationId, providerAccount, confidentialClient, createUserOnSignIn, name, provider, clientId, authority, enabled, created, updated) VALUES ('e2e-okta', 'ORGANIZATION', 'test-org', 'e2e.okta.com', false, false, 'e2e Okta', 'okta', 'e2e-client', 'https://e2e.okta.com', true, '2026-01-01', '2026-01-01') WITH REFRESH;

-- An organization the e2e organization user is not a member of, with an Okta configuration of its own: connecting it to
-- the user's account must be refused, since only an organization that admitted the account may have its directory
-- vouch for it
INSERT INTO kinotic_organization (id, name, description) VALUES ('e2e-other', 'e2e-other', 'Organization the e2e organization user does not belong to') WITH REFRESH;
INSERT INTO kinotic_oidc_configuration (id, type, organizationId, providerAccount, confidentialClient, createUserOnSignIn, name, provider, clientId, authority, enabled, created, updated) VALUES ('e2e-other-okta', 'ORGANIZATION', 'e2e-other', 'other.okta.com', false, false, 'Other Okta', 'okta', 'other-client', 'https://other.okta.com', true, '2026-01-01', '2026-01-01') WITH REFRESH;
