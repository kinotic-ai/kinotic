-- APPLICATION-scope users for the kinotic-js load-generator, which writes entity data through
-- an application client. One user per fixture application (healthcare, ecommerce), both under
-- organization kinotic-test, tenant default. Password for every user: kinotic. Client-side user
-- creation was removed, so these are seeded here rather than provisioned at runtime.

INSERT INTO kinotic_iam_user (id, email, displayName, authType, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000007', 'app-healthcare@kinotic.local', 'Load Generator Healthcare App User', 'LOCAL', 'kinotic-test', 'healthcare', 'default', true) WITH REFRESH;
INSERT INTO kinotic_iam_user (id, email, displayName, authType, organizationId, applicationId, tenantId, enabled) VALUES ('00000000-0000-0000-0000-000000000008', 'app-ecommerce@kinotic.local', 'Load Generator Ecommerce App User', 'LOCAL', 'kinotic-test', 'ecommerce', 'default', true) WITH REFRESH;

INSERT INTO kinotic_iam_credential (id, passwordHash) VALUES ('00000000-0000-0000-0000-000000000007', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
INSERT INTO kinotic_iam_credential (id, passwordHash) VALUES ('00000000-0000-0000-0000-000000000008', '$2b$12$ztUtxd/6nRYTACObjRNnMOisx3QlNuP2GmabcBdrv4Vcd6Vs46GaG') WITH REFRESH;
