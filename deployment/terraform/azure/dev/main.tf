# ── Developer UI publishing ───────────────────────────────────────────────────
# What a kinotic-server running on a developer machine needs to publish UIs to a real
# subscription: the resource group its organizations' storage accounts are created in, and
# the Front Door Standard profile and endpoint every site is served through, under
# apps-<environment>.<zone> in the global DNS zone, and the roles the server needs on them.
# The server creates the rest at runtime, as it does in the cluster. There is no VNet: the
# server reaches the accounts over their public endpoints and creates no private endpoints,
# which is what the `local` profile it runs with turns off. Independent of cluster/: apply
# and destroy freely.
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

  server_principal_object_id = coalesce(var.server_principal_object_id, data.azurerm_client_config.current.object_id)

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

# ── Roles for kinotic-server ──────────────────────────────────────────────────
# The developer's identity (see server_principal_object_id) gets what the cluster grants the
# kinotic-server workload identity in keyvault.tf, minus the private endpoint roles it has no
# VNet to use: Contributor creates the storage accounts, reads their keys and manages the
# Front Door origin groups, rule sets, domains and routes; Storage Blob Data Contributor
# is for the blob data plane the storage service reaches with the token, not the key;
# DNS Zone Contributor writes each site's CNAME and validation TXT.

resource "azurerm_role_assignment" "server_contributor" {
  scope                = azurerm_resource_group.main.id
  role_definition_name = "Contributor"
  principal_id         = local.server_principal_object_id
}

resource "azurerm_role_assignment" "server_blob_data" {
  scope                = azurerm_resource_group.main.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = local.server_principal_object_id
}

resource "azurerm_role_assignment" "server_dns" {
  scope                = local.global.dns_zone_id
  role_definition_name = "DNS Zone Contributor"
  principal_id         = local.server_principal_object_id
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

variable "server_principal_object_id" {
  description = "Object id of the principal kinotic-server authenticates as, which DefaultAzureCredential makes the service principal named by AZURE_CLIENT_ID when that is set where the server runs, and the az login user otherwise. Defaults to the identity running terraform; set it in local.auto.tfvars (gitignored) when the server uses a service principal"
  type        = string
  default     = null
}

# ── Outputs ───────────────────────────────────────────────────────────────────

output "sites_domain" {
  description = "The domain every published UI is a label under"
  value       = local.sites_domain
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
