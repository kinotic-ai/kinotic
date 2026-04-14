-- Add organizationId to all OrganizationScoped entities so strict dynamic mapping
-- accepts the new field populated by AbstractCrudService.save.
ALTER TABLE kinotic_application ADD COLUMN organizationId KEYWORD;
ALTER TABLE kinotic_project ADD COLUMN organizationId KEYWORD;
ALTER TABLE kinotic_entity_definition ADD COLUMN organizationId KEYWORD;
ALTER TABLE kinotic_named_query_service_definition ADD COLUMN organizationId KEYWORD;

-- Seed the kinotic-test organization used by end-to-end and core package tests
INSERT INTO kinotic_organization (id, name, slug, description) VALUES ('kinotic-test', 'kinotic-test', 'kinotic-test', 'Organization used by kinotic end-to-end and core package tests') WITH REFRESH;

-- Seed the kinotic-test organization user (password: kinotic)
INSERT INTO kinotic_iam_user (id, email, displayName, authType, authScopeType, authScopeId, enabled) VALUES ('00000000-0000-0000-0000-000000000002', 'kinotic@kinotic.local', 'Kinotic Test', 'LOCAL', 'ORGANIZATION', 'kinotic-test', true) WITH REFRESH;
INSERT INTO kinotic_iam_credential (id, passwordHash) VALUES ('00000000-0000-0000-0000-000000000002', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
