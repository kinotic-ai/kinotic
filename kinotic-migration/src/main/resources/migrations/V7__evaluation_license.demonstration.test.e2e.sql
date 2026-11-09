-- Install the Kinotic evaluation license key, which turns on every Enterprise Feature until the end of 2036, for
-- development and testing only: the local demonstration stack, the integration tests and the e2e suites. A cluster
-- of any other environment starts with no key installed, and a system user installs one on the system console's
-- License page.
INSERT INTO kinotic_installed_license_key (id, licenseKey, installed) VALUES ('installed', 'eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJLaW5vdGljIEV2YWx1YXRpb24gLSBkZXZlbG9wbWVudCBhbmQgdGVzdGluZyBvbmx5LCBub3QgZm9yIHByb2R1Y3Rpb24gdXNlIiwiaWF0IjoxNzkzODQxOTgxLCJleHAiOjIxMTQyOTQ0MDAsImZlYXR1cmVzIjpbIioiXX0.41zerfYDi74rzxfDfRqUE_WFK44lvtOt2MKAY9_ECmsCpDaWJO2MuLvU_NGfXgV69XxVS3pkxMSB0JGIa1hKRg', '2026-11-08T00:00:00Z') WITH REFRESH;
