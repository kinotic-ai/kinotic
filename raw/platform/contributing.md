---
title: "Contributing"
description: "How to contribute to the Kinotic OS project."
canonical_url: "https://kinotic.ai/platform/contributing"
---
# Contributing

> How to contribute to the Kinotic OS project.

## Overview

We welcome contributions to Kinotic OS. This guide covers the repository structure, build process, and contribution workflow.

## Repository Structure

<table>
<thead>
  <tr>
    <th>
      Directory
    </th>
    
    <th>
      Description
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        kinotic-core/
      </code>
    </td>
    
    <td>
      Java/Kotlin backend (Spring Boot) — RPC gateway, service registry, authentication
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-management-api/
      </code>
    </td>
    
    <td>
      Domain model and management API services — application, project, and cluster management
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-app-api/
      </code>
    </td>
    
    <td>
      The <code>
        app-api
      </code>
      
       services an application's runtimes and users call, hosted by the app server — the service directory registration of an application's services
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-ee/
      </code>
    </td>
    
    <td>
      Enterprise Features, licensed under the Kinotic Enterprise License — the Azure Key Vault secret storage backend
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-js/
      </code>
    </td>
    
    <td>
      TypeScript SDK workspace (Bun) — <code>
        @kinotic-ai/core
      </code>
      
      , <code>
        @kinotic-ai/persistence
      </code>
      
      , and related packages
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-frontend/
      </code>
    </td>
    
    <td>
      Vue.js UI workspace (pnpm) — <code>
        apps/portal
      </code>
      
       (the Kinotic OS dashboard), <code>
        apps/system
      </code>
      
       (the system console), <code>
        packages/common
      </code>
      
       (shared UI code, published to npm as <code>
        @kinotic-ai/frontend-common
      </code>
      
      )
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        website/
      </code>
    </td>
    
    <td>
      Documentation site (Docus/Nuxt)
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        deployment/
      </code>
    </td>
    
    <td>
      Helm charts, Docker Compose, Terraform configurations
    </td>
  </tr>
</tbody>
</table>

## Building

### Java/Kotlin Backend

```bash
./gradlew build
```

### TypeScript SDK

```bash
cd kinotic-js
bun install
bun run build
```

### Website

```bash
cd website
bun install
bun run dev
```

## Local development environment

Kinotic runs locally in Docker, from `deployment/docker-compose`, whether you are evaluating it or
developing it: the servers run the `demonstration` profile, host every project's repository on the
stack's own Gitea, and send no email, so nothing local needs Azure or GitHub. Evaluating, you run the
published images. Developing, you build the images of what you change from your working tree and run
the portal and system console from source against the stack.

### Prerequisites

- Docker
- For development, [Bun](https://bun.sh), pnpm and JDK 25

### Evaluating Kinotic

```bash
cd deployment/docker-compose
docker compose up -d
```

That brings up the whole platform from published images:

<table>
<thead>
  <tr>
    <th>
      Service
    </th>
    
    <th>
      Address
    </th>
    
    <th>
      What it is
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        kinotic-elasticsearch
      </code>
    </td>
    
    <td>
      <code>
        localhost:9200
      </code>
    </td>
    
    <td>
      Everything the platform stores
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-migration
      </code>
    </td>
    
    <td>
      —
    </td>
    
    <td>
      Creates the indices and seeds the system's rows, then exits; the servers wait for it
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-ui
      </code>
    </td>
    
    <td>
      <code>
        localhost:9090
      </code>
      
      , <code>
        localhost:9091
      </code>
    </td>
    
    <td>
      The portal on 9090 and the system console on 9091, built from <code>
        kinotic-frontend
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-server-management
      </code>
    </td>
    
    <td>
      <code>
        localhost:58503
      </code>
    </td>
    
    <td>
      The org API and STOMP gateway the portal calls
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-server-system
      </code>
    </td>
    
    <td>
      <code>
        localhost:58504
      </code>
    </td>
    
    <td>
      The API the system console and vm-manager nodes call
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-server-app
      </code>
    </td>
    
    <td>
      <code>
        localhost:58505
      </code>
    </td>
    
    <td>
      Every application's API, each at <code>
        <organizationId>--<applicationId>.localhost:58505
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-gen-schemas
      </code>
    </td>
    
    <td>
      —
    </td>
    
    <td>
      Publishes a set of sample entity definitions through the management server, then exits
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-azurite
      </code>
    </td>
    
    <td>
      <code>
        localhost:10000
      </code>
    </td>
    
    <td>
      The Azurite emulator every published UI is uploaded to, in place of the sites storage account
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-sites-edge
      </code>
    </td>
    
    <td>
      <code>
        localhost:8088
      </code>
    </td>
    
    <td>
      Serves each published UI from Azurite at <code>
        <app>--<ui>.<org>.sites.localhost:8088
      </code>
      
      , running the Cloudflare sites Worker itself
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-gitea
      </code>
    </td>
    
    <td>
      <code>
        localhost:3001
      </code>
    </td>
    
    <td>
      The Gitea every project's repository is created on, in place of GitHub; sign in as <code>
        kinotic_admin
      </code>
      
       / <code>
        kinotic-gitea-admin
      </code>
      
       to browse them
    </td>
  </tr>
  
  <tr>
    <td>
      Kibana
    </td>
    
    <td>
      <code>
        localhost:5601
      </code>
    </td>
    
    <td>
      Browses Elasticsearch
    </td>
  </tr>
  
  <tr>
    <td>
      OpenTelemetry collector, Tempo, Loki, Mimir, Grafana
    </td>
    
    <td>
      <code>
        localhost:3000
      </code>
      
       (Grafana)
    </td>
    
    <td>
      The servers' traces, logs and metrics
    </td>
  </tr>
</tbody>
</table>

Open `http://localhost:9090` and sign in as `kinotic@kinotic.local` with the password
`kinotic`. That user belongs to the `test-org` organization, which the stack fills with
sample data: the migration runs with the `fixtures` profile (`evaluation.env`), which seeds
applications, projects, members and pending invites, and `kinotic-gen-schemas` creates the
`ecommerce` and `healthcare` applications with published entity definitions and records.
Signing up instead creates an organization of your own, which starts empty.

<table>
<thead>
  <tr>
    <th>
      Screens with sample data
    </th>
    
    <th>
      Screens that stay empty
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Applications, their projects, members and settings; the organization's members and pending invites; entity definitions and their records in <code>
        ecommerce
      </code>
      
       and <code>
        healthcare
      </code>
      
      ; your profile
    </td>
    
    <td>
      Deployments, and the deployment sections of the application and project overviews; jobs; observability and traces
    </td>
  </tr>
</tbody>
</table>

The empty screens fill only from what a deployed project produces: a deployment starts when a
push to the project's repository reaches the management server, runs as workloads on a vm-manager
node, and sends the telemetry the observability screens read. The compose stack hosts every
project's repository on its own Gitea ([Repository host](https://kinotic.ai/platform/configuration#repository-host)),
so creating and deploying a project takes no GitHub, only a
[vm-manager node](#running-workloads-on-your-machine) to run the workloads.

The compose servers run the `demonstration` profile, which sends no email: signing up at
`http://localhost:9090/signup` goes straight to naming your organization and setting a password, and
adding a member shows the invitation's link to send on ([Demonstration server](https://kinotic.ai/platform/configuration#demonstration-server)).
A coding agent on the same machine (Claude Code, Codex or Grok Build) connects to its MCP server at
`http://localhost:58503/mcp`, added beside the Kinotic plugin; `deployment/docker-compose/README.md`
walks through it.
`docker compose pull` refreshes the images, and
`docker compose down -v` wipes Elasticsearch; the next `up` migrates and seeds it again.
`deployment/docker-compose/README.md` lists the other combinations of the stack.

### Developing Kinotic

Build the images of the servers you change, and the migration's when you change it, from your working
tree. Each is tagged `kinoticai/<module>:<version>`, the image compose runs, which it takes from your
machine rather than pulling it:

```bash
./gradlew :kinotic-server-management:bootBuildImage :kinotic-server-system:bootBuildImage \
          :kinotic-server-app:bootBuildImage :kinotic-migration:bootBuildImage
cd deployment/docker-compose
docker compose up -d
```

Rebuild an image and `docker compose up -d` again to replace its container. `kinotic-ui` and
`kinotic-sites-edge` run the `kinoticai/kinotic-ui` and `kinoticai/kinotic-sites-edge` images the CI
builds with the servers; after changing `kinotic-frontend`, the sites Worker
(`deployment/cloudflare/sites-worker/worker.js`) or the edge (`deployment/docker-compose/sites-edge.ts`),
add `--build` to build them from your working tree.

Run the portal from source against the stack:

```bash
cd kinotic-frontend
pnpm install
pnpm dev
```

The portal on `http://localhost:5173` calls the management server on `localhost:58503`, whose
`demonstration` profile accepts its origin. The links the server
builds, a sign-up's verification link or an invitation's accept link, open the portal `kinotic-ui`
serves on `http://localhost:9090`. `pnpm dev:system` runs the system console on
`http://localhost:5174`, which the system server's `demonstration` profile accepts too; its sign-ins
return to the console `kinotic-ui` serves on `http://localhost:9091`.

### Running workloads on your machine

A deployment runs its workloads on a vm-manager node. For local development that is the `BOXLITE`
provider on your own machine, which runs each workload as a micro VM: macOS on Apple silicon, or
Linux with `/dev/kvm`. The node works beside the compose servers: the system server answers on
`localhost:58504` and runs the `demonstration` profile.

```bash
cd kinotic-js/workspace
bun install && bun run build   # the node resolves the workspace packages through their dist
cd packages/vm-manager
bun run dev
```

`.env.development`, which Bun loads for `bun run dev`, supplies the rest: the node id
`dev-node-1`, the SYSTEM machine identity the demonstration migration seeds, and compose's Loki
and OTLP collector for workload logs, traces and metrics. The node registers with the system
server and shows `ONLINE` in the system console under **System → Worker nodes**. Guests reach
the org and app servers at `192.168.127.254`, boxlite's alias for the host, which the system
server's `demonstration` profile sets as `kinotic.systemApi.deployment.managementServer.host` and
`appServer.host`.

A push to a project's repository then deploys it: a sync workload checks the commit out, installs
it and syncs its entities, and a runtime workload starts each microservice. The first deployment
pulls `kinoticai/workload-runner`, which takes about a minute. A workload's logs are written under
`/tmp/kinotic-vm-logs/<workload id>` and shipped to Loki, where the logs on a project's deployment
page read them. A deployment that fails records why on its job run, under **Jobs** in the portal.

- A project's UIs are published to the Azurite emulator `compose.sites.yml` runs, and the local
edge beside it serves each at `http://<app>--<ui>.<org>.sites.localhost:8088`, the URL the
project's deployment page links. Chrome, Edge and Firefox resolve every `*.localhost` name to
this machine; a client that does not needs the hostname in its hosts file.
- The node keeps its image cache in `~/.boxlite`, which one process can hold at a time: stop the
node before running the vm-manager tests, or give the tests their own `BOXLITE_HOME`.
- The evaluator stack's sample nodes are marked unreachable on the system server's first
heartbeat pass, so deployments land on your node.

## Testing

### Java/Kotlin

```bash
./gradlew test
```

### TypeScript (Vitest)

```bash
cd kinotic-js
bun test
```

## Submitting Changes

1. Fork the repository and create a feature branch from `develop`
2. Make your changes with clear, descriptive commit messages
3. Ensure all tests pass before submitting
4. Submit a pull request against the `develop` branch
5. Accept the [Contributor License Agreement](https://github.com/kinotic-ai/kinotic/blob/develop/CLA.md)
on your first pull request by commenting `I have read the Kinotic CLA Document and I hereby sign the CLA`

## Licensing

Kinotic RPC (`kinotic-core/`, `kinotic-api-gateway/`, `kinotic-idl/`, and the `core` and `idl`
packages in `kinotic-js/workspace/packages/`) is licensed under the Apache License 2.0, and none of
its modules may depend on a module outside it: the build fails if one declares a project dependency on such a
module. `kinotic-ee/` holds the
Enterprise Features, licensed under the Kinotic Enterprise License. Everything else is licensed
under the Elastic License 2.0 with the Kinotic Additional Limitations. The root
[`LICENSE.md`](https://github.com/kinotic-ai/kinotic/blob/develop/LICENSE.md) routes each part of
the repository to its license. Any interface in an Elastic-licensed package with an `ee`
segment, such as `org.kinotic.domain.api.ee.services.SecretStorageBackend`, is an Enterprise
Extension Point: its implementations for production use are Enterprise Features and belong in
`kinotic-ee/`. [Enterprise License](https://kinotic.ai/platform/enterprise-license) describes what production use of
them requires.

The CLA grants Minds Ignited the right to license your contribution under any of these licenses,
including as part of an Enterprise Feature; you keep the copyright in it.


## Sitemap

See the full [sitemap](https://kinotic.ai/sitemap.md) for all pages.
