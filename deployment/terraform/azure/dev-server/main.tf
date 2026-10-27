# ── The development server's Azure side ──────────────────────────────────────
# What the shared development server (a Proxmox host, deployment/terraform/proxmox) needs from
# Azure and Cloudflare: the dev-environment module — a resource group, the sites storage
# account, the sites Worker and domain <environment>.<cloudflare zone> with the app server's
# hosts under it, a service principal with the roles the servers need — plus three things a
# developer's machine does without: a key vault the servers store secrets in, a storage account
# Elasticsearch snapshots go to, and in the platform zone the portal and the system console,
# which the sites Worker serves from the sites account, and the records that put the management
# and system servers' APIs on a hostname.
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
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.0"
    }
  }
}

provider "azurerm" {
  features {
    resource_group {
      # destroy removes the sites account's and the snapshot account's contents along with the group
      prevent_deletion_if_contains_resources = false
    }
    key_vault {
      purge_soft_delete_on_destroy    = false
      recover_soft_deleted_key_vaults = true
    }
  }
}

provider "azuread" {}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
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
  name_prefix         = "${var.project}-${var.environment}"
  global              = data.terraform_remote_state.global.outputs
  api_hostname        = "${var.api_label}.${local.global.dns_zone_name}"
  system_api_hostname = "${var.system_api_label}.${local.global.dns_zone_name}"
  portal_hostname     = "${var.portal_label}.${local.global.dns_zone_name}"
  console_hostname    = "${var.console_label}.${local.global.dns_zone_name}"
  # Summit 42's UI, on the sites Worker, and its server, behind the edge
  summit42_hostname     = var.summit42_enabled ? "dev-summit42.${local.global.dns_zone_name}" : null
  summit42_api_hostname = var.summit42_enabled ? "dev-summit42-api.${local.global.dns_zone_name}" : null
  apps_api_domain       = module.environment.apps_api_domain

  common_tags = {
    environment = var.environment
    project     = var.project
    managed_by  = "terraform"
  }
}

# ── The environment ───────────────────────────────────────────────────────────

module "environment" {
  source = "../modules/dev-environment"

  project     = var.project
  environment = var.environment
  location    = var.location
  tags        = local.common_tags

  email_communication_service_id = local.global.email_communication_service_id
  platform_zone_id               = local.global.dns_zone_id

  cloudflare_zone_name  = var.cloudflare_zone_name
  cloudflare_zone_id    = var.cloudflare_zone_id
  cloudflare_account_id = var.cloudflare_account_id
  # the app server's hosts, proxied to the router; the host's certificate must cover them
  app_server_address = var.public_ip
  # the portal, the system console and Summit 42's UI, served by the sites Worker from the sites
  # account at their hostnames in the platform zone, apart from the customers' sites, so no
  # published site is ever same-site with them or with the servers
  ui_hostnames    = compact([local.portal_hostname, local.console_hostname, local.summit42_hostname])
  portal_base_url = "https://${local.portal_hostname}"
}

# The operator uploads the two UIs into the sites account (deploy-ui.sh)
resource "azurerm_role_assignment" "operator_writes_sites" {
  scope                = module.environment.sites_storage_account_id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = data.azurerm_client_config.current.object_id
}

# ── Key Vault for the servers' secret storage ─────────────────────────────────
# Each server's SecretStorageService keeps every stored secret here (the in-memory backend
# would lose them on restart), and here is where they already are on the day the
# organizations move to the cloud. Secrets Officer: the servers write as well as read.

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

# ── The servers' hostnames ────────────────────────────────────────────────────
# An A record on the router's address for each name the servers answer to in the platform zone,
# the management server's, the system server's and Summit 42's; the app server's are in the customers' zone
# (the dev-environment module). Not proxied: the servers terminate TLS themselves. certbot on
# the host answers the DNS-01 challenges and kinotic-dyndns keeps the records on the router's
# current address, both with the DNS token the servers hold (secrets_env).

locals {
  public_hostnames = { for name, hostname in {
    api        = local.api_hostname
    system_api = local.system_api_hostname
    summit42   = local.summit42_api_hostname
  } : name => hostname if hostname != null }
}

# The records in the Azure zone are forgotten, not destroyed: the zone keeps answering while the
# registrar's change to Cloudflare propagates, and is deleted by hand with them afterwards
removed {
  from = azurerm_dns_a_record.public

  lifecycle {
    destroy = false
  }
}

resource "cloudflare_dns_record" "public" {
  for_each = local.public_hostnames

  zone_id = local.global.dns_zone_id
  name    = each.value
  type    = "A"
  content = var.public_ip
  proxied = false
  ttl     = 300
  comment = "The development server's router; kinotic-dyndns on the host keeps it current"

  # The host's updater owns the value once the record exists
  lifecycle {
    ignore_changes = [content]
  }
}
