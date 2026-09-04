# Push to Deploy

> How a git push to your project's repository becomes a running deployment.

## Overview

Every Kinotic project is backed by a git repository, and pushing to that repository's
**default branch** deploys the project. There is no CI to configure and no pipeline file to
maintain — the platform is the pipeline. Each qualifying push is verified, built, and
deployed as a tracked job you can watch live.

Only pushes to the default branch deploy. Pushes to other branches, branch deletions, and
tag operations are ignored.

## What a deployment does

A deployment runs as a job with six steps, visible with live progress on the project's
Deployment page (and, like any job, on the Jobs page):

1. **Resolve deployment target** — the first deployment picks a node with capacity and
creates the project's checkout directory on it; later deployments reuse the same node
and directory.
2. **Sync project source** — a short-lived, sandboxed build VM brings the checkout to the
pushed commit (an incremental fetch, not a fresh clone — installs stay warm), installs
dependencies, finds the [artifacts](#artifacts) the commit contains, and compiles the
project. This step is the build gate: a commit that does not build, or whose packages
are not named validly, never reaches your running services. Your entity definitions are
synchronized and published here, so an entity added in this commit has its backing
storage created and is ready for data operations; entities already published keep
serving. Pending migrations in `./migrations` are applied in the same step. The
artifacts it found are reported to the platform, and only after everything succeeded
does it signal the runtime to reload. The step expands on the job run page into the
build VM's log, live while it runs; the VM is kept after its run, whatever the outcome,
so the log stays readable until the next deployment retires it.
3. **Resolve artifacts** — binds the artifacts the build VM reported for this commit into
the deployment run. The step's row on the job run page lists them: each microservice
with the module it starts from, and each UI.
4. **Ensure runtime workloads** — one long-lived VM per microservice the commit contains,
each running that microservice from the checkout (mounted read-only) with its own machine
identity. The first deployment of a microservice starts its VM. Later deployments leave a
running VM alone: its supervisor sees the reload signal and restarts the service onto the
new commit. A VM that was stopped or has crashed, or whose entry module the commit moved,
is retired and replaced by a fresh one, never started again with the state that may have
failed it. A microservice the commit no longer contains keeps its VM running but its
deployment is marked orphaned, and a commit that brings it back adopts the VM; the
deployment is only ever destroyed on request. A microservice that cannot be left running
is recorded failed with the reason, the others still deploy, and the step fails naming it.
5. **Publish UIs** — uploads every UI the build VM built to your organization's storage
through a short-lived publish VM that holds nothing but an upload URL valid for the run,
then records each UI's site. The storage was created with your organization, so the step
only reads it; a UI's first publish mints its site's hostname and creates the site, which
is the one slow step a deployment ever takes on the platform's cloud, and later publishes
keep the site and switch it to the new commit, retaining the previous commit's assets so
tabs still open on it keep working. A UI the commit no longer
contains keeps serving but its deployment is marked orphaned, and a commit that brings it
back adopts the site. The step expands on the job run page into the publish VM's log.

The runtime supervisor restarts your services as whole processes, with escalating backoff
if they crash immediately after a reload.

## Artifacts

A project repository is a Bun workspace, and every deployment finds the
[artifacts](/apps/application-structure/artifact-types) it contains by where each package
sits in the tree. Two kinds are deployed today:

- A **microservice** is a directory directly under `packages/microservices` holding a
`package.json`. Its entry module is the `main` of that `package.json`, or `src/main.ts`
when it declares none.
- A **UI** is a directory directly under `packages/ui` whose `package.json` declares a
`build` script. A package under `packages/ui` without one is a library, not a UI, and
is left alone.

An artifact's identity is the unscoped part of the `name` in its `package.json`:
`@acme/orders` is the microservice `orders`, `@acme/admin` the UI `admin`. The directory
name never matters, so a package can be moved or its directory renamed without changing
what the platform knows it as. The unscoped name must be lowercase letters, digits, and
interior dashes, and two artifacts of the same kind cannot share one; either problem fails
the deployment in the build VM with a message in its log naming the package. Nested
`package.json` files deeper in a package are never artifacts of their own.

The artifacts are found inside the build VM, over the checked-out commit, and recorded on
the project's deployment together with the commit they were found in.

## Machine identities

Every VM connects to Kinotic as a machine identity the deployment provisions for the project,
one for the build VM and one per microservice, recorded with the deployment. They are
organization-scope: synchronizing entity definitions and publishing services into your
application's zone are things your organization does on its own behalf, so an identity scoped
to a single application would not be enough to do either. The project's Deployment page lists
them, so you can see which credentials exist for a project and what each one is used for.

Kinotic stores only a hash of a machine's secret, so a secret can never be handed out a
second time. Every deployment therefore issues the build VM a **new** secret, and the one
the previous build used stops working at that moment. A microservice's secret is issued
when its VM is created and stays valid for that VM's life — a later deployment reloads
the service inside the running VM rather than replacing it.

A project's machines can be disabled or removed like any other machine, which cuts the
corresponding workload off at its next connection. Removing one is not permanent damage: the
next deployment provisions a replacement.

## Microservices

The project's Deployment page lists each microservice the deployment has ensured: its status,
the commit it was last ensured for, the module it runs, and its workload. From there you can
open the microservice's log, restart its VM in place, or remove the deployment. Removing
destroys the VM and its machine identity and forgets the record; a microservice the current
commit still contains comes back with the next deployment, so removal is mainly how an
orphaned microservice, one a commit dropped, is finally retired.

<table>
<thead>
  <tr>
    <th>
      Status
    </th>
    
    <th>
      Meaning
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        DEPLOYED
      </code>
    </td>
    
    <td>
      The VM is up and running the microservice as of the ensured commit
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        FAILED
      </code>
    </td>
    
    <td>
      The last deployment could not leave the microservice running; the message says why
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ORPHANED
      </code>
    </td>
    
    <td>
      The last deployed commit no longer contains the microservice; the VM keeps running until removed
    </td>
  </tr>
</tbody>
</table>

## UIs

Each UI is served from its own site under the platform's sites domain, at a hostname the
first publish mints from your organization, application and UI names. The site serves the
UI as of the commit recorded on its deployment, and a publish switches it atomically: the
new commit's assets are uploaded first, under a path named by the commit and cached for a
year, and the site's index is replaced last.

<table>
<thead>
  <tr>
    <th>
      Status
    </th>
    
    <th>
      Meaning
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        PROVISIONING
      </code>
    </td>
    
    <td>
      The site is being created: its hostname is registered and its certificate issued
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        READY
      </code>
    </td>
    
    <td>
      The site serves the UI as of the recorded commit
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ORPHANED
      </code>
    </td>
    
    <td>
      The last deployed commit no longer contains the UI; the site keeps serving until removed
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        FAILED
      </code>
    </td>
    
    <td>
      The site could not be created; the message says why
    </td>
  </tr>
</tbody>
</table>

A failed site can be provisioned again, which completes whatever the earlier attempt left
missing. Removing a UI deployment takes its site down, deletes the UI's published files and
deletes the record; a UI the current commit still contains comes back with the next
deployment, at a site minted anew. Deleting the project removes every one of its UI
deployments the same way.

The project's deployment page lists each UI with its site, status and the commit the site
serves, and offers both actions; the project's and the application's overview pages list the
published UIs with their sites as well. A tab left open on a site keeps working after a publish,
since the commit it was built from stays published alongside the new one; it can find out it
is behind with `checkUiVersion` from `@kinotic-ai/core` (see
[the UI build contract](/apps/application-structure/applications-and-projects#the-ui-build-contract)).

## Deployment status

The project's deployment record tracks one status at a time:

<table>
<thead>
  <tr>
    <th>
      Status
    </th>
    
    <th>
      Meaning
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        DEPLOYING
      </code>
    </td>
    
    <td>
      A deployment job is running for the latest qualifying push
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        RUNNING
      </code>
    </td>
    
    <td>
      The recorded commit built successfully and every microservice's VM is serving it
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        FAILED
      </code>
    </td>
    
    <td>
      The last deployment failed; the reason is recorded on the status
    </td>
  </tr>
</tbody>
</table>

The project's Deployment page shows this status alongside the deployed commit and the
most recent deployment job's steps, live while a deployment runs — so it always answers
exactly what the last push did, step by step.

## Failure behavior

A failed build fails the deployment and leaves the previously deployed commit running —
the runtime VMs are only ever signaled after a successful sync. The build VM of a failed sync
is kept (not discarded) so its logs can be inspected from the workload logs view; it is
cleaned up on the next successful deployment of the project.

## Concurrent pushes

Deployments are serialized per project with **latest-wins**: pushes arriving while a
deployment is running collapse to the newest commit, which deploys next. Intermediate
commits are skipped rather than queued — the checkout converges to the newest push, and
each skipped commit is still reachable in git history. Redeliveries of the same push are
harmless: syncing a commit twice converges to the same checkout.
