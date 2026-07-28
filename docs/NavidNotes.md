### IamUser refactor

* Fix DefaultPendingRegistrationService.applyPendingScope (Should not be needed)
* Review login handlers and KinoticSecurityService in detail.
* Verify DefaultOpenAPIService.addNamedQueryPathItems (call looks up named queries without org)
* No OpenAPI routes have the org in the path.

### App auth return-to-application redirect

* App OIDC login callbacks (`ApplicationLoginHandler` → `redirectSuccess`) currently land app
  end-users on the web app `/` because no per-app frontend URL exists in the model. The
  return-to-application redirect belongs with the per-app URL / distributable-components work
  already deferred. Same gap applies to where app-invite emails link (today: the hosted
  `/invite/accept` page, which is the intended fallback).

  
### Outstanding
* Move secret storage stuff out of the kinotic-core
* Fix OidcFlowOrchestrator.java to not secretReferenceResolver.resolve for finding secrets. This won't work for our customers’ configs and should be done differently for our signup configs. 
* Move get rid of kinotic-core/src/main/java/org/kinotic/core/api/config/SslHelper.java


# Grafana Labs
* We may want the multi tenancy to be org.app.tenant , so metrics can be displayed to our customers’ users. 

# KinoticIgniteClusterManager
* revisit the statusFlux and changesFlux AI thinks they are good, I feel like they are redundant.|

# Docs
Make sure the ServiceDirectory logic is documented for TS code, once we finish implementing it.


# Kinotic TS
We store a bunch of maps during decorator processing that will not be used. We need to formalize this into the TS-Morph stuf we are going to do. Left here in case I forgot.


# Kinotic OS Security
* Add flags to specify what users are allowed to login, i.e. System, Org, App. This will allow us to only allow System logins from behind a  VPN.
  * Make sure this flag also affects if a JWT can be minted, basically will require the org or app id based on the allowed login hierarchy.
