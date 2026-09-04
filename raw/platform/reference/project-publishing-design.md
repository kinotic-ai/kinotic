# Project Publishing Design

> How a project's microservices and UIs are deployed, stored, and served, and how far the build has come.

## Overview

This page is the design record for publishing a project's artifacts: every microservice
under `packages/microservices` runs in a VM of its own, and every UI under `packages/ui`
is built by the deployment, uploaded to organization-owned Azure Blob storage, and served
on its own hostname through Azure Front Door. It describes the target shape, and the
[Built so far](#built-so-far) section at the end says which parts exist today. The
user-facing view of the same flow is [Push to Deploy](/apps/deployment/push-to-deploy).

## Entities

```text
ProjectDeployment            one per project: checkout dir, sync and publish VMs, job run, status,
  │                          the artifacts the last sync found and the commit it found them in
  ├─ MicroserviceDeployment  one per microservice artifact: its VM, its machine identity, the
  │                          commit it was ensured for, status
  └─ UiDeployment            one per UI artifact: hostname label, Front Door resources, live
                             commit, status
```

"Deployment" names standing deployed state, as `ProjectDeployment` already does. Both
children are plain `AbstractRepository` entities in management-api beside `Workload`, not
organization-scoped ones. Each child has a published service for the console with
`findAllForProject` and `remove`; removal is the only path that destroys anything.

Module placement follows the two servers the platform is splitting into: a management server
running management-api alone, which the portal and every organization machine reach, and a
system server running management-api and system-api, which the system console reaches. So
the records, their repositories and every service the portal calls live in management-api;
everything that touches nodes, workloads or Azure lives in system-api; and the management
plane reaches the second only through `DeploymentOperationsProxy`, a `@Proxy` interface onto
the system zone's `DeploymentOperationsService` (`restartMicroservice`, `removeMicroservice`,
`checkUiSite`, `provisionUiSite`, `removeUiSite`, `provisionOrganization`). The management
services authorize a request against the caller's organization, then delegate; the
operations service trusts its callers.
`ProjectDeployment` keeps the sync identity and loses `runtimeWorkloadId` and
`runtimeMachineIdentityId`, which move to the per-microservice rows.

## Artifacts and their identity

The [artifacts](/apps/application-structure/artifact-types) a commit contains are found
inside the sync VM, over the checkout, by TypeScript in the workload-runner: code that sits
in the Bun ecosystem and can later resolve packages the way the project's own tooling
(`bunup`) does. For now the rule is naive and reads no Bun or bunup configuration: a
microservice is a package directly under `packages/microservices` with entry
`package.json.main`, else `src/main.ts`; a UI is a package directly under `packages/ui` with
a `build` script that writes `dist/index.html`. The UI location is the one requirement that
stays hard-coded when the resolution improves.

An artifact's identity is the unscoped part of the `name` in its `package.json`
(`@acme/admin` is `admin`), a single zone label; a name that is not one fails the deploy
naming the package. The directory name never matters. The sync VM reports what it found
through `ProjectArtifactService.recordArtifacts`, authenticated as the project's sync
machine identity, and the server records it on `ProjectDeployment` with the commit it was
found in; the server applies the same name rule to what it is told.

## The deploy job

<table>
<thead>
  <tr>
    <th>
      #
    </th>
    
    <th>
      Task
    </th>
    
    <th>
      What it does
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      1
    </td>
    
    <td>
      Resolve deployment target
    </td>
    
    <td>
      <code>
        DeployTarget(nodeId, hostDir, syncWorkloadId, uiPublishWorkloadId)
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      2
    </td>
    
    <td>
      Sync project source
    </td>
    
    <td>
      git fetch/checkout · bun install · find artifacts · <code>
        kinotic sync --publish
      </code>
      
       · build UIs · report artifacts · write the sentinel (still last)
    </td>
  </tr>
  
  <tr>
    <td>
      3
    </td>
    
    <td>
      Resolve artifacts
    </td>
    
    <td>
      binds the <code>
        ProjectArtifacts(microservices, uis)
      </code>
      
       the sync VM reported for the commit into the run; fails when the record names another commit. <code>
        Store.state(ARTIFACTS).wire()
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      4
    </td>
    
    <td>
      Ensure runtime workloads
    </td>
    
    <td>
      one VM per microservice artifact with one machine identity each; running ones restart on the sentinel, ended ones are replaced, missing ones created, vanished ones marked <code>
        ORPHANED
      </code>
      
      . <code>
        Store.state(MICROSERVICE_DEPLOYMENTS).wire()
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      5
    </td>
    
    <td>
      Publish UIs
    </td>
    
    <td>
      requires the organization's storage <code>
        READY
      </code>
      
       (provisioned with the organization, never here) → container SAS → publish VM uploads → finalize rows; vanished UIs marked <code>
        ORPHANED
      </code>
      
      . <code>
        Store.state(UI_DEPLOYMENTS).wire()
      </code>
    </td>
  </tr>
</tbody>
</table>

Placement: every VM of a project shares the node holding its checkout. A first deployment's
probe wants `syncMemoryMb + microservices × runtimeMemoryMb`, but the artifacts are only known
after the sync VM has run on the chosen node; how the probe accounts for the microservices is
decided with the runtime-workload phase. The runtime workload of
each service sets `KINOTIC_APP_ENTRY = <dir>/<entry>`; the supervisor is unchanged.
`ProjectDeployIdentityService.issueRuntimeCredentials` reads and writes the identity id on
the `MicroserviceDeployment` row; the sync identity stays on `ProjectDeployment`.

## Storage

One storage account per organization, named `"kin" + hex(sha256(organizationId)).substring(0, 21)`:
StorageV2, LRS, hierarchical namespace on, TLS 1.2, `allowBlobPublicAccess=false`, public
network access open (Front Door reads from addresses the storage firewall cannot name, and
every read is authorized by the bearer token of the profile's identity), a private endpoint in the platform
VNet registered in `privatelink.blob.core.windows.net` unless
`kinotic.systemApi.organizationStorage.disablePrivateEndpoint` is set, as it is where the server
runs outside the VNet, tagged `org=<id>`. It holds one container, `sites` (container names
are 3 to 63 characters, so not `ui`).

Recorded on `Organization.storage`, an `OrganizationStorage`: `azureSubscriptionId`,
`azureAccountName`, `azureBlobEndpoint` and `status` (`PROVISIONING`, `READY` or `FAILED`,
with a message). Provisioning is a grind job, `provision-organization-<id>`, with a **Provision
storage** task and a **Prepare Front Door** task, both idempotent, owned by the organization
and recorded on it as `provisioningJobRunId`. `OrganizationService.provision` runs every
`OrganizationProvisioner` (a domain hook) on the organization: the signup flow calls it once
the organization's record is complete, and `SystemOrganizationService.provisionOrganization`
runs it again on request. The management module's provisioner asks the system server to
start the job through the proxy and returns; the tasks record their outcome on the
organization and the run shows in the system console's job runs and on the organization's
overview, with a **Provision again** action. A deployment only
reads the outcome, and fails naming the state when the storage is not `READY`; nothing is
provisioned by a deployment. A mock provisioner under
`kinotic.systemApi.organizationStorage.disableProvisioner=true` points every organization
at the configured Azurite connection string.

```text
kin<hash>/sites/prod/orders/ui/admin/index.html          Cache-Control: no-cache; uploaded last: the atomic switch
kin<hash>/sites/prod/orders/ui/admin/version.json        Cache-Control: no-cache; { "commitSha": "<sha>" }
kin<hash>/sites/prod/orders/ui/admin/<sha>/assets/…      Cache-Control: public, max-age=31536000, immutable
```

`prod` is one constant in the prefix builder; nothing else knows the environment. The
finalize step deletes every sha directory except the current one and the one it replaced.

## Serving

One Front Door Standard profile and endpoint, created by terraform, with a system-assigned
managed identity that terraform grants **Storage Blob Data Reader** on the resource group
every organization's storage account is created in. Per organization, created with the
organization once its storage is ready and named by id by every site's route: origin group
`org-<orgId>`, authenticating to the origin as that identity (`SystemAssignedIdentity`,
scope `https://storage.azure.com/.default`), with HTTPS health probes on the container's
properties, which origin authentication requires, and origin `<account>.blob.core.windows.net`
with the same origin host header. Front Door puts the identity's bearer token on every
request it forwards, so no SAS travels in the configuration and a request's own query string
reaches the origin unchanged. Origin authentication exists from API version `2025-06-01`,
which the Java CDN SDK does not speak yet, so the provisioner writes the origin group as JSON
through the SDK's pipeline and polls the SDK's read until it is provisioned.

Shared by every route, created once, the rule set `sites` with one rule:

```text
spa    url_file_extension GreaterThan 0, negated  → rewrite /  →  /index.html     preserve_unmatched_path = false
```

`url_file_extension Any` matches a path with no extension as well, so it cannot tell a file
from a route of the single-page application; the extension's length can. A request naming a
file reaches the origin as it is, under the route's origin path.

Per site, created on first publish: a custom domain `<label>.<sitesDomain>` with a managed
certificate; DNS in the `kinotic.ai` zone, `CNAME <label>.apps → <profile endpoint host>` and
`TXT _dnsauth.<label>.apps → <validation token>`; and a route for that domain with pattern
`/*`, HTTPS only with redirect (origin authentication requires HTTPS to the origin), the
organization's origin group, origin path `/sites/prod/<app>/ui/<ui>`, the shared rule set,
caching on and query strings ignored. A route naming another rule set is written again.
Front Door writes are slow, serialized per profile, and answer 409 when one is in flight, so
the provisioner issues one write at a time per profile with backoff; a change takes up to
15 minutes to reach every edge, longer when changes queue. A site is `PROVISIONING` until
`https://<hostname>/version.json` answers through Front Door with the deployment's commit
and `https://<hostname>/` answers with HTML (the root unrewritten is the UI's directory, an
empty 200, and a file bypasses the spa rule), then `READY`; the domain's validation and
certificate flags say nothing about the route, the rule set or the propagation, so they are
not consulted, and an earlier configuration of the same site may still answer while a new
one propagates. `FAILED` with the message when the
domain's validation cannot succeed, with `retryProvisioning`. The provisioner checks a
provisioning site every 30 seconds for up to two hours after provisioning it and records the
outcome on its row; a site still provisioning after that, or one whose polling died with the
server, is checked again whenever its project's UI deployments are listed.

The hostname label is `{org}-{app}-{ui}` under `sitesDomain` (`apps.kinotic.ai`), minted once
at first publish, stored as `UiDeployment.id`, looked up by hostname and never parsed. The
repository enforces uniqueness with a numeric suffix on collision. A label is at most 63
characters, else the publish fails naming the organization and application. There is no
wildcard DNS record: each site has its own CNAME.

## Auth

`*.apps.kinotic.ai` is same-site with `api.kinotic.ai`, so the session cookie works unchanged
and CORS already admits `(.+\.)?kinotic\.ai`. The session cookie is named
`__Host-kinotic-session` (Secure and `Path=/` are already set). `apps.kinotic.ai` is never
put on the Public Suffix List.

## Build and upload contracts

The sync step sets, per UI build: `KINOTIC_UI_BASE_PATH=/<sha>/`, `KINOTIC_UI_COMMIT=<sha>`,
and `KINOTIC_UI_SERVER_URL` (from `DomainProperties.resolveApiBaseUrl()`, placed on the sync
workload by the job factory; `DeploymentProperties.serverHost` is an IPv4 for egress and not
usable by a browser). A build that leaves no `dist/index.html` fails the deploy before the
sentinel. `artifacts.ts` in workload-runner is the one enumeration of the artifacts, shared
by the sync and publish entrypoints.

The publish workload is named `project-ui-publish-<projectId>`, with id
`DeployTarget.uiPublishWorkloadId` decided in `resolveTarget` like `syncWorkloadId`. It runs
the same image in the foreground with entrypoint `bun src/publish-ui.ts`, the checkout mounted
read-only at `/workspace`, env `KINOTIC_UI_COMMIT`, and the secret `KINOTIC_UI_UPLOAD_URL` =
`<blob endpoint>/sites/prod/<app>/ui?<container SAS, create+write, TTL the run>`. Its allowed
hosts are the host that upload URL names only — the organization's account, which the
platform network resolves to its private endpoint, or the Azurite standing in for it; it carries no Kinotic
credentials and no machine identity, is kept after its run, and is retired by the next run's
`resolveTarget`. Its exit check is the one `syncSource` uses, extracted to one method with two
consumers. `publish-ui.ts` uploads, per UI, everything in `dist` except `index.html` under
`<name>/<sha>/` with the immutable header, then `version.json`, then `index.html`, each a
`PUT` with `x-ms-blob-type: BlockBlob`, `x-ms-blob-cache-control`, and a `Content-Type` from
`Bun.file().type`; small concurrency, one retry on 5xx, non-zero exit on failure.

## Orphans and removal

An artifact missing from a deploy marks its deployment `ORPHANED`; it keeps running or serving
and nothing is deleted. An artifact that returns is adopted (status back to `DEPLOYED` or
`READY`), never re-provisioned. `MicroserviceDeploymentService.remove` destroys the VM,
removes the identity, and deletes the row. `UiDeploymentService.remove` deletes the route, the
domain, both DNS records, the blob prefix, and the row. Both confirm in the console when the
deployment is not orphaned.

## Stale tabs

The template compares `__KINOTIC_UI_COMMIT__` with `/version.json` (fetched with
`cache: 'no-store'`) on focus and route change, through a `checkUiVersion(builtCommit)` helper
exported by `@kinotic-ai/core`. There is no push mechanism.

## Properties and dependencies

`kinotic.systemApi.organizationStorage.*`: `subscriptionIds`, `resourceGroup`, `location`,
`privateEndpointSubnetId`, `privateDnsZoneId`, `azuriteConnectionString`, `disableProvisioner`,
`disablePrivateEndpoint`.
`kinotic.systemApi.uiDeployment.*`: `sitesDomain`, `dnsZoneId`, `frontDoorProfileId`,
`frontDoorEndpointHostName`, `disableProvisioner`. Nothing else is a property.

Managed artifacts, each a `*Version` in `gradle.properties` and one line in the conventions
`dependencyManagement` block: `com.azure:azure-storage-blob`,
`com.azure:azure-resourcemanager-storage`, `com.azure:azure-resourcemanager-network`,
`com.azure:azure-resourcemanager-privatedns`, `com.azure:azure-resourcemanager-dns`,
`com.azure:azure-resourcemanager-cdn`. `azure-identity` is already managed.

## Deferred

Not part of this design: customer domains, an Environment entity, customer file storage, push
notification of publishes, and service endpoints instead of private endpoints.

## Built so far

- **Artifact discovery.** The sync VM finds the commit's artifacts (`src/artifacts.ts` in the
workload-runner, the naive rule above) and reports them through
`ProjectArtifactService.recordArtifacts`, which records `artifacts` and
`artifactsCommitSha` on `ProjectDeployment`. The deploy job's third task, **Resolve
artifacts**, binds them into the run as a `ProjectArtifacts` (its `MicroserviceArtifact`s
and `UiArtifact`s) under the `artifacts` store name, wired to watchers. The job run page
of the portal and of the system console lists what the commit contains on that task's row,
and the sync task's row expands into the build VM's log in both.
- **One VM per microservice.** `MicroserviceDeployment` rows, keyed `<projectId>:<name>` so
the store enforces one per microservice, record each microservice's workload, machine
identity, entry point and commit. The deploy job's fourth task, **Ensure runtime workloads**,
keeps running VMs, replaces ended ones and ones whose entry point moved, creates missing ones,
orphans vanished ones, records failures per row, and stores the rows under
`microserviceDeployments`. `ProjectDeployment` no longer carries a runtime workload or
identity; `findProjectMachines` lists the sync identity then one per microservice.
- **Microservices in the console.** `MicroserviceDeploymentService` (`findAllForProject`,
`restart`, `remove`), published from management-api and reaching the VM through
`DeploymentOperationsProxy`, and the portal's deployment page: a microservices table with logs, restart and remove, and the
machines labelled by the deployment that records them.
- **Organization storage.** `AzureOrganizationStorageProvisioner` creates the account, the
`sites` container, and, unless `disablePrivateEndpoint` is set, the private endpoint with its
DNS zone group, recording the outcome on
`Organization` with a status of `PROVISIONING`, `READY` or `FAILED`. It is the first task
of the `provision-organization-<id>` job that `DeploymentOperationsService` runs on the
system server when the organization is created, asked through the proxy by
`DefaultOrganizationProvisioner`, the management module's `OrganizationProvisioner`, and
whenever the system console's **Provision again** asks; the Front Door preparation is the
second. The provisioners, the storage service and their settings live in system-api. A
deployment that publishes a UI reads the outcome and fails when the storage is not ready.
`AzureProvisioningIntegrationTest` in system-api runs both provisioners against a
developer's subscription, from the `local` profile and `.env.local`, and skips elsewhere.
`MockOrganizationStorageProvisioner` points every organization at Azurite. Terraform owns the resource group, private-endpoints subnet, private DNS zone and the
kinotic-server roles. The account's public network stays open, with anonymous access off,
so Front Door can read it; the platform comes in through the private endpoint, or over the
public endpoint where it has none. A developer runs the real path against their own
subscription with the `deployment/terraform/azure/dev` root and the `local` profile.
- **UIs built in the sync VM.** After the entity sync, `sync.ts` runs `bun run build` in
every UI artifact with `KINOTIC_UI_BASE_PATH`, `KINOTIC_UI_COMMIT` and
`KINOTIC_UI_SERVER_URL`, the last placed on the sync workload from
`DomainProperties.resolveApiBaseUrl()`, and fails the run naming a UI whose build leaves no
`dist/index.html`. The template repository's Vite config must honor the three variables.
- **UI deployments and the storage data plane.** `UiDeployment` rows keyed by the site's
hostname label, `OrganizationStorageService` (`issueUploadUrl`, `exists`, `listCommitDirs`,
`deletePrefix`) over the blob SDK or Azurite, `UiStoragePaths` as the one home of the
container layout, and the `UiDeploymentProvisioner` contract with a mock that marks sites
ready at once.
- **The publish task.** The deploy job's fifth task, **Publish UIs**, runs
`project-ui-publish-<projectId>` under `DeployTarget.uiPublishWorkloadId` with
`publish-ui.ts`, the checkout read-only, `KINOTIC_UI_COMMIT`, the secret
`KINOTIC_UI_UPLOAD_URL` (a one-hour container SAS) and the storage account's hostname as its
only egress, then finalizes: mints labels with numeric suffixes on collision, provisions new
sites, adopts returning ones, orphans vanished ones, keeps the current and previous commit
directories and deletes the rest. The exit check is shared with the sync task, and the
previous publish workload is retired by the next run's target resolution.
- **Sites on Front Door.** `FrontDoorUiDeploymentProvisioner` creates, per organization
when it is provisioned, the origin group `org-<orgId>` on the storage account's blob host,
authenticating as the profile's managed identity, and once the shared rule set `sites`
with the `spa` rewrite rule; per site the custom domain with a managed certificate, the
CNAME and `_dnsauth` TXT records in the platform zone, and the route with the UI's prefix
as origin path, naming the organization's origin group and the shared rule set by id.
Every step is get-or-create, a lapsed validation gets a new token, profile writes are
queued and retried on 409, and a provisioning site is polled in the background until
`version.json` serves its commit through Front Door and it is `READY`, or it is `FAILED`.
`remove` deletes the route, the domain and the two records. `AzureUtil` classifies the
management plane's 404 and 409 for both provisioners. Terraform (`frontdoor.tf`) owns the
profile, its identity and that identity's Storage Blob Data Reader on the organization
storage group, and grants the server CDN Profile Contributor and DNS Zone Contributor; the
storage account's public network is open so Front Door can read it.
- **UI deployments in the console.** `UiDeploymentService` (`findAllForProject`,
`retryProvisioning`, `remove`), published from management-api and delegating to the
system server through `DeploymentOperationsProxy`: listing has any site left provisioning
for ten minutes or more checked, so one whose polling died with the server still advances;
retry runs the provisioner again and records the outcome; remove takes the site down,
deletes the UI's prefix in storage and deletes the row. Deleting a project removes
its UI deployments the same way. `@kinotic-ai/management-api` exposes it as
`Kinotic.uiDeployments` with the `UiDeployment` model.
- **Sites in the console, and the tab contract.** `UiDeployment.url` is minted with the
label, so the portal's deployment page lists each UI with a link to its site, status and
commit, with retry and removal. The session cookie is `__Host-kinotic-session`, its name
shared through `EventConstants.SESSION_COOKIE_NAME` by the gateway's session handler and the
`/api/auth/me` route. `checkUiVersion` in `@kinotic-ai/core` reads a site's `version.json`
and reports a tab stale once the site serves another commit.
