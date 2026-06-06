### IamUser refactor

* Still need to update the PendingRegistration to not use AuthScope Type / ID
* Fix DefaultPendingRegistrationService.applyPendingScope (Should not be needed)
* Review login handlers and KinoticSecurityService in detail. 
* Fix JS code
* Verify DefaultOpenAPIService.addNamedQueryPathItems (call looks up named queries without org)
