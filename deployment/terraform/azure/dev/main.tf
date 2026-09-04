# ── Developer UI publishing ───────────────────────────────────────────────────
# What a kinotic-server running on a developer machine needs to publish UIs to a real
# subscription: the resource group its organizations' storage accounts are created in, the
# Front Door Standard profile and endpoint every site is served through, under
# apps-<environment>.<zone> in the global DNS zone, and a service principal for the server
# holding the roles it needs on them and on the email service. The server creates the rest
# at runtime, as it does in the cluster. There is no VNet: the server reaches the accounts
# over their public endpoints and creates no private endpoints, which is what the `local`
# profile it runs with turns off. Independent of cluster/: apply and destroy freely.
#
# State is kept locally, next to this file, because the root is per developer: each
# developer picks an `environment` of their own and owns what it creates.

terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 3.0"
    }
  }
}

provider "azurerm" {
  features {
    resource_group {
      # The group fills with the storage accounts and Front Door resources the server
      # creates at runtime; destroy removes them along with it
      prevent_deletion_if_contains_resources = false
    }
  }
}

provider "azuread" {}

# ── Read global state ─────────────────────────────────────────────────────────

data "terraform_remote_state" "global" {
  backend = "azurerm"
  config = {
    resource_group_name  = "rg-kinotic-tfstate"
    storage_account_name = "stkinotictfstate"
    container_name       = "tfstate"
    key                  = "global/terraform.tfstate"
  }
}

data "azurerm_client_config" "current" {}

locals {
  name_prefix  = "${var.project}-${var.environment}"
  global       = data.terraform_remote_state.global.outputs
  sites_domain = "apps-${var.environment}.${local.global.dns_zone_name}"
  env_local    = "${path.module}/../../../../.env.local"

  common_tags = {
    environment = var.environment
    project     = var.project
    managed_by  = "terraform"
  }
}

# ── Resource Group ────────────────────────────────────────────────────────────
# Holds the Front Door profile and, created at runtime, one storage account per organization

resource "azurerm_resource_group" "main" {
  name     = "rg-${local.name_prefix}"
  location = var.location
  tags     = local.common_tags
}

# ── UI sites on Front Door ────────────────────────────────────────────────────

resource "azurerm_cdn_frontdoor_profile" "sites" {
  name                = "afd-${local.name_prefix}-sites"
  resource_group_name = azurerm_resource_group.main.name
  sku_name            = "Standard_AzureFrontDoor"
  tags                = local.common_tags
}

resource "azurerm_cdn_frontdoor_endpoint" "sites" {
  name                     = "sites-${local.name_prefix}"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.sites.id
  tags                     = local.common_tags
}

# ── Service principal for kinotic-server ──────────────────────────────────────
# The server on this machine authenticates as this principal: DefaultAzureCredential takes
# AZURE_CLIENT_ID, AZURE_CLIENT_SECRET and AZURE_TENANT_ID before anything else, and the
# root writes those three to .env.local at the repository root. One per developer, holding
# roles on nothing but what this root creates and the email service.

resource "azuread_application" "server" {
  display_name = "${local.name_prefix}-server"
}

resource "azuread_service_principal" "server" {
  client_id = azuread_application.server.client_id
}

resource "azuread_application_password" "server" {
  application_id = azuread_application.server.id
  display_name   = "${local.name_prefix}-server"
}

# Keeps the block below current and everything else in the file as it was: a block a
# previous run wrote (its comment through AZURE_TENANT_ID) and any stray AZURE_* lines are
# dropped, trailing blank lines trimmed, and the block appended after one blank line. The
# secret travels through the environment rather than the command, so it stays out of
# terraform's output. The script is among the triggers, so editing it rewrites the file on
# the next apply.
locals {
  env_local_script = <<-EOT
    set -eu
    f="${local.env_local}"
    touch "$f"
    awk '
      /^# kinotic-server Azure identity/ { skip = 1 }
      skip && /^AZURE_TENANT_ID=/ { skip = 0; next }
      skip { next }
      /^AZURE_(CLIENT_ID|CLIENT_SECRET|TENANT_ID)=/ { next }
      { lines[++n] = $0; if ($0 !~ /^[[:space:]]*$/) last = n }
      END { for (i = 1; i <= last; i++) print lines[i] }
    ' "$f" > "$f.tmp"
    if [ -s "$f.tmp" ]; then printf '\n' >> "$f.tmp"; fi
    cat >> "$f.tmp" <<EOF
    # kinotic-server Azure identity: the ${local.name_prefix}-server service principal, created by
    # deployment/terraform/azure/dev with the roles the server needs to publish UIs and send email.
    # DefaultAzureCredential reads these before anything else. Written by terraform apply there;
    # to write them again: terraform apply -replace=terraform_data.env_local
    AZURE_CLIENT_ID=$AZURE_CLIENT_ID
    AZURE_CLIENT_SECRET=$AZURE_CLIENT_SECRET
    AZURE_TENANT_ID=$AZURE_TENANT_ID
    EOF
    mv "$f.tmp" "$f"
  EOT
}

resource "terraform_data" "env_local" {
  triggers_replace = [azuread_application_password.server.key_id, local.env_local_script]

  provisioner "local-exec" {
    command = local.env_local_script
    environment = {
      AZURE_CLIENT_ID     = azuread_application.server.client_id
      AZURE_CLIENT_SECRET = azuread_application_password.server.value
      AZURE_TENANT_ID     = data.azurerm_client_config.current.tenant_id
    }
  }
}

# ── Roles for kinotic-server ──────────────────────────────────────────────────
# What the cluster grants the kinotic-server workload identity in keyvault.tf and email.tf,
# minus the private endpoint roles it has no VNet to use: Contributor creates the storage
# accounts, reads their keys and manages the Front Door origin groups, rule sets, domains
# and routes; Storage Blob Data Contributor is for the blob data plane the storage service
# reaches with the token, not the key; DNS Zone Contributor writes each site's CNAME and
# validation TXT; Contributor on the email service sends mail.

resource "azurerm_role_assignment" "server_contributor" {
  scope                = azurerm_resource_group.main.id
  role_definition_name = "Contributor"
  principal_id         = azuread_service_principal.server.object_id
  # a principal created moments ago may not have replicated to the RBAC lookup yet
  skip_service_principal_aad_check = true
}

resource "azurerm_role_assignment" "server_blob_data" {
  scope                            = azurerm_resource_group.main.id
  role_definition_name             = "Storage Blob Data Contributor"
  principal_id                     = azuread_service_principal.server.object_id
  skip_service_principal_aad_check = true
}

resource "azurerm_role_assignment" "server_dns" {
  scope                            = local.global.dns_zone_id
  role_definition_name             = "DNS Zone Contributor"
  principal_id                     = azuread_service_principal.server.object_id
  skip_service_principal_aad_check = true
}

resource "azurerm_role_assignment" "server_email" {
  scope                            = local.global.email_communication_service_id
  role_definition_name             = "Contributor"
  principal_id                     = azuread_service_principal.server.object_id
  skip_service_principal_aad_check = true
}

# ── Variables ─────────────────────────────────────────────────────────────────

variable "environment" {
  description = "Names everything this root creates and the apps-<environment> sites domain; one per developer, since a site hostname is bound to one Front Door profile in all of Azure"
  type        = string
  default     = "local"
}

variable "project" {
  description = "Project name"
  type        = string
  default     = "kinotic"
}

variable "location" {
  description = "Azure region"
  type        = string
  default     = "centralus"
}

# ── Outputs ───────────────────────────────────────────────────────────────────

output "sites_domain" {
  description = "The domain every published UI is a label under"
  value       = local.sites_domain
}

output "server_client_id" {
  description = "The service principal kinotic-server runs as; its credentials are in .env.local at the repository root"
  value       = azuread_application.server.client_id
}

output "application_local_yml" {
  description = "The `local` profile kinotic-server runs with: write it to kinotic-server/src/main/resources/application-local.yml"
  value       = <<-EOT
    kinotic:
      systemApi:
        organizationStorage:
          disableProvisioner: false
          subscriptionIds: ["${data.azurerm_client_config.current.subscription_id}"]
          resourceGroup: ${azurerm_resource_group.main.name}
          location: ${var.location}
        uiDeployment:
          disableProvisioner: false
          sitesDomain: ${local.sites_domain}
          dnsZoneId: ${local.global.dns_zone_id}
          frontDoorProfileId: ${azurerm_cdn_frontdoor_profile.sites.id}
          frontDoorEndpointHostName: ${azurerm_cdn_frontdoor_endpoint.sites.host_name}
  EOT
}
