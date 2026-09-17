# Developer UI Publishing

Everything a kinotic-server on your machine needs to publish UIs to a real Azure
subscription, instead of the no-op site provisioner the development profile uses. One apply
creates:

| Resource | Name | Purpose |
|---|---|---|
| Resource group | `rg-kinotic-<environment>` | Holds everything below but the service principal |
| Front Door Standard profile + endpoint | `afd-kinotic-<environment>-sites` | Serves every published UI at `<label>.apps-<environment>.kinotic.ai` through one wildcard domain, one wildcard DNS record and one route; nothing on Front Door changes when a UI is published (`modules/sites`) |
| Sites storage account | `stkinotic<environment>sites` | Holds every site's files under `sites/<hostname>/`, read by the profile's identity and written by the publish workloads through URLs the server signs |
| Key vault | `kv-kinotic-<environment>-sites` | Holds the Let's Encrypt wildcard certificate for `*.apps-<environment>.kinotic.ai`, issued by the apply through a DNS challenge and renewed by an apply within 30 days of expiry |
| Service principal | `kinotic-<environment>-server` | The identity the server runs as, with Storage Blob Data Contributor on the sites account and Contributor on the email service |
| `.env.local` at the repository root | | The principal's `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET` and `AZURE_TENANT_ID`, written by the apply |

The wildcard DNS records are written into the shared `kinotic.ai` zone under
`apps-<environment>`, so the zone itself is not created here. There is no VNet: the publish
workloads reach the account over its public endpoint. State is local, in this directory, and
gitignored.

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

```hcl
# local.auto.tfvars (gitignored)
lets_encrypt_email = "you@example.com"   # the Let's Encrypt account the wildcard certificate is issued under
```

```bash
terraform init
terraform apply -target=module.sites.azurerm_cdn_frontdoor_profile.sites   # the profile first: the roles need its identity's principal id
terraform apply
terraform output -raw application_local_yml > ../../../../kinotic-server/src/main/resources/application-local.yml
```

The last command writes the `local` Spring profile, gitignored, which turns the site
provisioner on and names the sites domain and the account. `.env.local` at the repository root now carries the
principal's credentials; if the file existed, its other lines are untouched.

## Checking the setup

Publish one site from a test, before starting the server. It reads `application-local.yml`,
takes the principal from `.env.local`, uploads through the URLs the server would sign, reads
back through Front Door what it published, and checks that a URL acts on one site's directory
alone, so whatever Azure rejects fails naming the call:

```bash
cd ../../../..
./gradlew :kinotic-system-api:test --tests '*AzureProvisioningIntegrationTest*'
```

It publishes the site `azure-it.apps-<environment>.kinotic.ai` and leaves it, so a second run
is quick.

## Running the server

Start kinotic-server with `.env.local` in its environment and both profiles active:

```bash
SPRING_PROFILES_ACTIVE=development,local
```

`DefaultAzureCredential` takes the three `AZURE_*` variables before anything else, so the
server signs as the principal whatever `az login` is signed in as.

If your local Elasticsearch predates this, drop the organization index and the migration
history so the current mapping is created:

```bash
curl -XDELETE 'localhost:9200/kinotic_organization'
curl -XDELETE 'localhost:9200/migration_history'
```

Then deploy a project that contains a UI: its site appears on the deployment page,
`PROVISIONING` while the publish workload uploads it and Front Door serves the first request,
then `READY` with its URL. A publish before the roles propagated fails with
`AuthorizationFailed` on the workload; the next deploy of the project succeeds.

## Tearing down

```bash
terraform destroy
```

This removes the resource group with the sites account, the Front Door profile and the key
vault, the wildcard records under `apps-<environment>` in the `kinotic.ai` zone, the service
principal, and its role assignments. It does not touch the three lines in `.env.local`.

## Troubleshooting

| Symptom | Cause |
|---|---|
| `subscription_id is a required provider property` | `ARM_SUBSCRIPTION_ID` is not exported in this shell |
| `Error acquiring the state lock` with nothing running | A previous run was interrupted; `terraform force-unlock <ID>` with the id from `.terraform.tfstate.lock.info` |
| A publish workload fails with `AuthorizationFailed` right after apply | Role assignments take a minute or two to become visible; deploy the project again |
| The server still authenticates as an old principal | The three `AZURE_*` variables are set elsewhere in its environment, ahead of `.env.local` |
