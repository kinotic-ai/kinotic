# ── The development server's Azure side ──────────────────────────────────────
# What the shared development server (a Proxmox host, deployment/terraform/proxmox) needs from
# Azure: the dev-environment module — a resource group, the Front Door profile and endpoint
# under apps-<environment>.<zone>, the sites storage account and key vault, a service
# principal with the roles the server needs — plus three things a developer's machine does
# without: a key vault the server stores secrets in, a storage account Elasticsearch snapshots
# go to, the portal and the system console served by the same Front Door as the published
# sites, and the DNS record and rights that put the API on a hostname with a certificate.
#
# State is local, like dev/: one server, applied from one machine. The proxmox root reads
# this root's outputs from that state file.

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
    # The sites module: origin authentication at an API version azurerm lacks, and the
    # wildcard certificate's issuance
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

provider "azurerm" {
  features {
    resource_group {
      # The group fills with the storage accounts and Front Door resources the server
      # creates at runtime; destroy removes them along with it
      prevent_deletion_if_contains_resources = false
    }
    key_vault {
      purge_soft_delete_on_destroy    = false
      recover_soft_deleted_key_vaults = true
    }
  }
}

provider "azuread" {}

provider "azapi" {}

provider "acme" {
  server_url = "https://acme-v02.api.letsencrypt.org/directory"
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
  api_hostname = "${var.api_label}.${local.global.dns_zone_name}"

  common_tags = {
    environment = var.environment
    project     = var.project
    managed_by  = "terraform"
  }
}

# ── The environment ───────────────────────────────────────────────────────────

module "environment" {
  source = "../modules/dev-environment"

  project            = var.project
  environment        = var.environment
  location           = var.location
  tags               = local.common_tags
  lets_encrypt_email = var.lets_encrypt_email

  dns_zone_name                  = local.global.dns_zone_name
  dns_zone_id                    = local.global.dns_zone_id
  dns_zone_resource_group_name   = local.global.resource_group_name
  dns_zone_subscription_id       = local.global.subscription_id
  email_communication_service_id = local.global.email_communication_service_id

  ui_hostnames = { portal = var.portal_label, console = var.console_label }
}

# The operator uploads the two UIs into the sites account (deploy-ui.sh)
resource "azurerm_role_assignment" "operator_writes_sites" {
  scope                = module.environment.sites_storage_account_id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = data.azurerm_client_config.current.object_id
}

# ── Key Vault for the server's secret storage ─────────────────────────────────
# The server's SecretStorageService keeps every stored secret here (the in-memory backend
# would lose them on restart), and here is where they already are on the day the
# organizations move to the cloud. Secrets Officer: the server writes as well as reads.

resource "azurerm_key_vault" "server" {
  name                       = "kv-${local.name_prefix}"
  location                   = var.location
  resource_group_name        = module.environment.resource_group_name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  soft_delete_retention_days = 7
  rbac_authorization_enabled = true
  tags                       = local.common_tags
}

resource "azurerm_role_assignment" "server_key_vault_secrets" {
  scope                            = azurerm_key_vault.server.id
  role_definition_name             = "Key Vault Secrets Officer"
  principal_id                     = module.environment.server_principal_object_id
  skip_service_principal_aad_check = true
}

# The operator places the social sign-in providers' client secrets, which the seeded
# kinotic_org_signup_oidc_configuration rows name by secretNameRef
resource "azurerm_role_assignment" "operator_key_vault_secrets" {
  scope                = azurerm_key_vault.server.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

# ── Storage for Elasticsearch snapshots ───────────────────────────────────────
# The nightly snapshot repository, and the vehicle the data moves to the cloud in. The
# cluster authenticates with the account key from its keystore, so no role is needed.

resource "azurerm_storage_account" "snapshots" {
  name                     = "st${var.project}${var.environment}snapshots"
  resource_group_name      = module.environment.resource_group_name
  location                 = var.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"
  tags                     = local.common_tags
}

resource "azurerm_storage_container" "snapshots" {
  name                  = "elasticsearch-snapshots"
  storage_account_id    = azurerm_storage_account.snapshots.id
  container_access_type = "private"
}

# ── The API's hostname ────────────────────────────────────────────────────────
# One A record for the address the router forwards to kinotic-server, and DNS Zone
# Contributor so certbot on the host answers the DNS-01 challenge, and kinotic-dyndns keeps
# the record on the router's current address, as the server's principal.

resource "azurerm_dns_a_record" "api" {
  name                = var.api_label
  zone_name           = local.global.dns_zone_name
  resource_group_name = local.global.resource_group_name
  ttl                 = 300
  records             = [var.public_ip]
  tags                = local.common_tags

  # The host's updater owns the value once the record exists
  lifecycle {
    ignore_changes = [records]
  }
}

resource "azurerm_role_assignment" "server_dns" {
  scope                            = local.global.dns_zone_id
  role_definition_name             = "DNS Zone Contributor"
  principal_id                     = module.environment.server_principal_object_id
  skip_service_principal_aad_check = true
}
