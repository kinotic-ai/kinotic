### IamUser refactor

* Still need to update the PendingRegistration to not use AuthScope Type / ID
* Fix DefaultPendingRegistrationService.applyPendingScope (Should not be needed)
* Review login handlers and KinoticSecurityService in detail. 
* Fix JS code
* Verify DefaultOpenAPIService.addNamedQueryPathItems (call looks up named queries without org)

### App auth return-to-application redirect

* App OIDC login callbacks (`ApplicationLoginHandler` → `redirectSuccess`) currently land app
  end-users on the web app `/` because no per-app frontend URL exists in the model. The
  return-to-application redirect belongs with the per-app URL / distributable-components work
  already deferred. Same gap applies to where app-invite emails link (today: the hosted
  `/invite/accept` page, which is the intended fallback).
