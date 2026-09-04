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
      
       (the platform-operator console), <code>
        packages/common
      </code>
      
       (shared UI code)
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

### Publishing UIs against Azure

The development profile publishes UIs to a local Azurite and marks every site ready without
Front Door. To exercise the real path — a storage account per organization, sites served
through Front Door — point the server at a subscription of your own.

Create what the server cannot create itself, once:

```bash
az login
cd deployment/terraform/azure/dev
terraform init
terraform apply   # environment = "local" in terraform.tfvars; pick a name of your own
terraform output -raw application_local_yml > ../../../../kinotic-server/src/main/resources/application-local.yml
```

That is a resource group, a Front Door Standard profile with an endpoint, the
`apps-<environment>.<zone>` sites domain in the platform's DNS zone, and a service principal
for the server with the roles it needs: Contributor and Storage Blob Data Contributor on the
group, DNS Zone Contributor on the zone, and Contributor on the email service, so it sends
mail too. The written file is the git-ignored `local` profile: it turns both provisioners on
and names those resources. The `environment` is yours alone: a site hostname can be bound to
one Front Door profile in all of Azure, so two developers sharing a sites domain would
collide.

The principal's `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET` and `AZURE_TENANT_ID` are written to
`.env.local` at the repository root, which git ignores, as a commented block after a blank
line. When the file exists its other lines are kept and the block a previous apply wrote is
replaced, or appended if absent. Run the server with that file
in its environment; `DefaultAzureCredential` takes those variables before anything else, so
the server provisions as the principal whatever `az login` is signed in as. A role
assignment takes a minute or two to become visible; an organization provisioned before that
fails with `AuthorizationFailed`, and **Provision again** finishes it once the roles have
propagated.

Run the server with both profiles:

```bash
SPRING_PROFILES_ACTIVE=development,local
```

The `local` profile overrides the development one, which already disables private endpoints:
a developer machine is outside any platform VNet, so the server and the publish workload reach
each storage account over its public endpoint, and the workload's egress allowlist names that
host.

Then sign up an organization, or open an existing one in the system console and choose
**Provision again**: the `provision-organization-<id>` job creates its storage account and
prepares its Front Door origin group and rule set, and the organization's overview shows the
outcome. Deploy a project that contains a UI; the deployment publishes it and provisions its
site, served at `https://<label>.apps-<environment>.<zone>` once Front Door has validated the
domain and issued its certificate, which takes a few minutes and shows as the deployment
turning from provisioning to ready.

`terraform destroy` removes the resource group with every storage account and Front Door
resource the server created in it. The CNAME and validation TXT records of sites live in the
platform's DNS zone, outside the group; removing a deployment removes them, so remove your
deployments first, or delete the records under `apps-<environment>` by hand.

## Submitting Changes

1. Fork the repository and create a feature branch from `develop`
2. Make your changes with clear, descriptive commit messages
3. Ensure all tests pass before submitting
4. Submit a pull request against the `develop` branch
