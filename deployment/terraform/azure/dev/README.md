# Developer UI Publishing

Everything a kinotic-server on your machine needs to publish UIs to a real Azure
subscription, instead of the Azurite and no-op site provisioner the development profile
uses. One apply creates:

| Resource | Name | Purpose |
|---|---|---|
| Resource group | `rg-kinotic-<environment>` | Holds the Front Door profile and, created by the server at runtime, one storage account per organization |
| Front Door Standard profile + endpoint | `afd-kinotic-<environment>-sites` | Serves every published UI at `<label>.apps-<environment>.kinotic.ai`; its system identity holds Storage Blob Data Reader on the group, and the server adds origin groups, the rule set, domains and routes at runtime |
| Service principal | `kinotic-<environment>-server` | The identity the server runs as, with Contributor and Storage Blob Data Contributor on the group, DNS Zone Contributor on `kinotic.ai`, and Contributor on the email service |
| `.env.local` at the repository root | | The principal's `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET` and `AZURE_TENANT_ID`, written by the apply |

Site DNS records are written into the shared `kinotic.ai` zone under `apps-<environment>`,
so the zone itself is not created here. There is no VNet: the server reaches the storage
accounts over their public endpoints, which the `local` profile's `disablePrivateEndpoint`
allows for. State is local, in this directory, and gitignored.

## Prerequisites

- `az` and `terraform` (`brew install azure-cli terraform`)
- On the subscription: rights to create resources and role assignments (Owner, or Contributor
  plus User Access Administrator), and rights to create app registrations in the tenant
- Read access to the `stkinotictfstate` storage account, where the global root's outputs
  (DNS zone, email service) are read from

## Getting started

```bash
az login
export ARM_SUBSCRIPTION_ID=$(az account show --query id -o tsv)   # the azurerm provider needs it named

cd deployment/terraform/azure/dev
```

Pick an environment name of your own in `terraform.tfvars`. It names everything above and
the `apps-<environment>` sites domain, and a site hostname can be bound to one Front Door
profile in all of Azure, so two developers sharing one would collide:

```hcl
environment = "local"   # e.g. your first name
```

```bash
terraform init
terraform apply -target=azurerm_cdn_frontdoor_profile.sites   # the profile first: the role below needs its identity's principal id
terraform apply
terraform output -raw application_local_yml > ../../../../kinotic-server/src/main/resources/application-local.yml
```

The last command writes the `local` Spring profile, gitignored, which turns both provisioners
on and names the resources above. `.env.local` at the repository root now carries the
principal's credentials; if the file existed, its other lines are untouched.

## Checking the setup

Provision a fixed organization and one site from a test, before starting the server. It
reads `application-local.yml`, takes the principal from `.env.local`, and reads back from Azure
what each step created, so whatever Azure rejects fails naming the call:

```bash
cd ../../../..
./gradlew :kinotic-system-api:test --tests '*AzureProvisioningIntegrationTest*'
```

It creates the storage account of `kinotic-azure-it` in the resource group and the site
`azure-it.apps-<environment>.kinotic.ai`, and leaves them, so a second run is quick.

## Running the server

Start kinotic-server with `.env.local` in its environment and both profiles active:

```bash
SPRING_PROFILES_ACTIVE=development,local
```

`DefaultAzureCredential` takes the three `AZURE_*` variables before anything else, so the
server provisions as the principal whatever `az login` is signed in as.

If your local Elasticsearch predates this, drop the organization index and the migration
history so the current mapping is created:

```bash
curl -XDELETE 'localhost:9200/kinotic_organization'
curl -XDELETE 'localhost:9200/migration_history'
```

Then sign up a new organization. Its overview in the system console shows the provisioning
job creating the storage account and preparing Front Door; deploy a project that contains a
UI and its site appears on the deployment page, `PROVISIONING` for a few minutes while Front
Door validates the domain and issues the certificate, then `READY` with its URL. An
organization created before the roles propagated fails with `AuthorizationFailed`;
**Provision again** on its overview finishes it.

## Tearing down

```bash
terraform destroy
```

This removes the resource group with every storage account and Front Door resource the
server created in it, the service principal, and its role assignments. Two things it does not
touch: the site CNAME and validation TXT records in the `kinotic.ai` zone, which the server
removes when a deployment is removed, so remove your deployments first or delete the records
under `apps-<environment>` by hand; and the three lines in `.env.local`.

## Troubleshooting

| Symptom | Cause |
|---|---|
| `subscription_id is a required provider property` | `ARM_SUBSCRIPTION_ID` is not exported in this shell |
| `Error acquiring the state lock` with nothing running | A previous run was interrupted; `terraform force-unlock <ID>` with the id from `.terraform.tfstate.lock.info` |
| Organization storage `FAILED` with `AuthorizationFailed` right after apply | Role assignments take a minute or two to become visible; **Provision again** |
| The server still authenticates as an old principal | The three `AZURE_*` variables are set elsewhere in its environment, ahead of `.env.local` |
