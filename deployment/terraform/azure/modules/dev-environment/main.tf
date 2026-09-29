# What a kinotic-server outside AKS needs from Azure to publish UIs and send email: a resource
# group its organizations' storage accounts are created in, the Front Door Standard profile
# and endpoint every site is served through under apps-<environment>.<zone>, the key vault
# the wildcard certificate is issued into, and a service principal for the server holding the
# roles it needs on them and on the email service. The server creates the rest at runtime, as
# it does in the cluster. There is no VNet: the server reaches the accounts over their public
# endpoints and creates no private endpoints.
#
# Two roots use it: dev/ for a developer's own machine, dev-server/ for the shared
# development server. Each picks an environment name, since a site hostname is bound to one
# Front Door profile in all of Azure.

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
    acme = {
      source  = "vancluever/acme"
      version = "~> 2.0"
    }
  }
}

data "azurerm_client_config" "current" {}

locals {
  name_prefix = "${var.project}-${var.environment}"
}

# ── Resource Group ────────────────────────────────────────────────────────────
# Holds the Front Door profile and, created at runtime, one storage account per organization

resource "azurerm_resource_group" "main" {
  name     = "rg-${local.name_prefix}"
  location = var.location
  tags     = var.tags
}

# ── UI sites on Front Door ────────────────────────────────────────────────────
# The sites module owns everything a site needs, so nothing changes on Front Door or in
# DNS when a UI is published; this module adds the key vault the wildcard certificate is
# issued into.

resource "azurerm_key_vault" "sites" {
  name                       = "kv-${local.name_prefix}-sites"
  location                   = var.location
  resource_group_name        = azurerm_resource_group.main.name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  soft_delete_retention_days = 7
  rbac_authorization_enabled = true
  tags                       = var.tags
}

module "sites" {
  source = "../sites"

  name_prefix                     = local.name_prefix
  location                        = var.location
  resource_group_name             = azurerm_resource_group.main.name
  tags                            = var.tags
  dns_zone_name                   = var.dns_zone_name
  dns_zone_id                     = var.dns_zone_id
  dns_zone_resource_group_name    = var.dns_zone_resource_group_name
  dns_zone_subscription_id        = var.dns_zone_subscription_id
  sites_label                     = "apps-${var.environment}"
  key_vault_id                    = azurerm_key_vault.sites.id
  certificate_officer_object_id   = data.azurerm_client_config.current.object_id
  lets_encrypt_email              = var.lets_encrypt_email
  server_principal_id             = azuread_service_principal.server.object_id
  server_principal_skip_aad_check = true
  hostnames                       = var.ui_hostnames
}

# ── Service principal for kinotic-server ──────────────────────────────────────
# The server authenticates as this principal: DefaultAzureCredential takes AZURE_CLIENT_ID,
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

# ── Roles for kinotic-server ──────────────────────────────────────────────────
# What the cluster grants the kinotic-server workload identity: the sites module gives it
# Storage Blob Data Contributor on the sites account, where it signs each site's upload and
# removal URLs; Contributor on the email service sends mail.

resource "azurerm_role_assignment" "server_email" {
  scope                = var.email_communication_service_id
  role_definition_name = "Contributor"
  principal_id         = azuread_service_principal.server.object_id
  # a principal created moments ago may not have replicated to the RBAC lookup yet
  skip_service_principal_aad_check = true
}
