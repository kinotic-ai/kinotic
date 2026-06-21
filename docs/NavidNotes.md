### IamUser refactor

* Fix DefaultPendingRegistrationService.applyPendingScope (Should not be needed)
* Review login handlers and KinoticSecurityService in detail.
* Verify DefaultOpenAPIService.addNamedQueryPathItems (call looks up named queries without org)

### App auth return-to-application redirect

* App OIDC login callbacks (`ApplicationLoginHandler` → `redirectSuccess`) currently land app
  end-users on the web app `/` because no per-app frontend URL exists in the model. The
  return-to-application redirect belongs with the per-app URL / distributable-components work
  already deferred. Same gap applies to where app-invite emails link (today: the hosted
  `/invite/accept` page, which is the intended fallback).

  
### Outstanding
* Move secret storage stuff out of the kinotic-core
* Fix OidcFlowOrchestrator.java to not secretReferenceResolver.resolve for finding secrets. This won't work for our customers’ configs and should be done differently for our signup configs. 


