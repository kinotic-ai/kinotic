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


# UI
* Add link to github repo from project


# Grafana Labs
* We may want the multi tenancy to be org.app.tenant , so metrics can be displayed to our customers’ users. 

# KinoticIgniteClusterManager
* revisit the statusFlux and changesFlux AI thinks they are good, I feel like they are redundant.|

# ServiceDirectory review leftovers
* ServiceRegistrationBeanPostProcessor still publishes to the directory when the RPC
  registration just failed (the catch logs and falls through) — a `return` in the catch would
  make directory publishing conditional on the service actually serving.
* A streaming return hidden inside an async wrapper escapes the @McpTool rejection:
  `CompletableFuture<Flux<T>>` converts to AsyncC3Type(StreamC3Type) and the
  `instanceof StreamC3Type` check in DefaultServiceDirectory.buildEntry only sees the top level.
  Unwrap AsyncC3Type.valueType before checking. (Parity with the old reflection — not a regression.)

# Docs
Make sure the ServiceDirectory logic is documented for TS code, once we finish implementing it.


# Kinotic TS
We store a bunch of maps during decorator processing that will not be used. We need to formalize this into the TS-Morph stuf we are going to do. Left here in case I forgot.