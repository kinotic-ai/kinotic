# What the Kinotic servers outside AKS need to publish UIs and send email: a resource group the
# sites storage account is created in, the Cloudflare side of the environment's sites domain,
# <environment>.<cloudflare zone>, where the sites Worker serves every site from the account,
# and a service principal for the servers holding the roles they need on the account and on
# the email service. The system server creates the rest at runtime, as it does in the cluster.
# There is no VNet: the servers reach the accounts over their public endpoints and create no
# private endpoints.
#
# Two roots use it: dev/ for a developer's own machine, dev-server/ for the shared
# development server. Each picks an environment name, which is its label under the zone.

terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 3.0"
    }
    azapi = {
      source  = "Azure/azapi"
      version = "~> 2.0"
    }
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.0"
    }
  }
}

data "azurerm_client_config" "current" {}

locals {
  name_prefix = "${var.project}-${var.environment}"
}

# ── Resource Group ────────────────────────────────────────────────────────────
# Holds the sites storage account and, with named hostnames, the Front Door profile serving them

resource "azurerm_resource_group" "main" {
  name     = "rg-${local.name_prefix}"
  location = var.location
  tags     = var.tags
}

# ── UI sites ─────────────────────────────────────────────────────────────────
# The sites module owns the storage account every published UI is written to and, for the
# named hostnames a root asks for (a development server's portal and console), the Front Door
# profile serving them; the cloudflare-sites module owns the sites domain itself, where the
# sites Worker serves every published UI from the account and the app server's hosts live.

module "sites" {
  source = "../sites"

  name_prefix                     = local.name_prefix
  location                        = var.location
  resource_group_name             = azurerm_resource_group.main.name
  tags                            = var.tags
  dns_zone_name                   = var.dns_zone_name
  dns_zone_id                     = var.dns_zone_id
  dns_zone_resource_group_name    = var.dns_zone_resource_group_name
  server_principal_id             = azuread_service_principal.server.object_id
  server_principal_skip_aad_check = true
  hostnames                       = var.ui_hostnames
}

module "cloudflare_sites" {
  source = "../cloudflare-sites"

  zone_id                     = var.cloudflare_zone_id
  zone_name                   = var.cloudflare_zone_name
  sites_subdomain             = var.environment
  account_id                  = var.cloudflare_account_id
  worker_name                 = "${local.name_prefix}-sites"
  app_server_address          = var.app_server_address
  sites_storage_blob_endpoint = module.sites.storage_blob_endpoint
  portal_base_url             = var.portal_base_url
}

# ── Service principal for the Kinotic servers ─────────────────────────────────
# The servers authenticate as this principal: DefaultAzureCredential takes AZURE_CLIENT_ID,
# AZURE_CLIENT_SECRET and AZURE_TENANT_ID before anything else. One per environment, holding
# roles on nothing but what this module creates and the email service.

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

# ── Roles for the Kinotic servers ─────────────────────────────────────────────
# What the cluster grants the servers' workload identity: the sites module gives it
# Storage Blob Data Contributor on the sites account, where it signs each site's upload and
# removal URLs; Contributor on the email service sends mail.

resource "azurerm_role_assignment" "server_email" {
  scope                = var.email_communication_service_id
  role_definition_name = "Contributor"
  principal_id         = azuread_service_principal.server.object_id
  # a principal created moments ago may not have replicated to the RBAC lookup yet
  skip_service_principal_aad_check = true
}
