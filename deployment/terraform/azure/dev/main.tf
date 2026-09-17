# ── Developer UI publishing ───────────────────────────────────────────────────
# What a kinotic-server running on a developer machine needs to publish UIs to a real
# subscription: the dev-environment module (a resource group, the Front Door profile and
# endpoint under apps-<environment>.<zone>, the sites storage account and key vault, and a
# service principal with the roles the server needs), plus the three lines in .env.local that
# make the server on this machine run as that principal.
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

locals {
  global    = data.terraform_remote_state.global.outputs
  env_local = "${path.module}/../../../../.env.local"

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
}

# The resources predate the module and keep their identity: a developer's next apply moves
# them in state instead of recreating them. The first two chain through the earlier move of
# the profile and endpoint into the sites module.
moved {
  from = azurerm_cdn_frontdoor_profile.sites
  to   = module.sites.azurerm_cdn_frontdoor_profile.sites
}

moved {
  from = azurerm_cdn_frontdoor_endpoint.sites
  to   = module.sites.azurerm_cdn_frontdoor_endpoint.sites
}

moved {
  from = module.sites
  to   = module.environment.module.sites
}

moved {
  from = azurerm_resource_group.main
  to   = module.environment.azurerm_resource_group.main
}

moved {
  from = azurerm_key_vault.sites
  to   = module.environment.azurerm_key_vault.sites
}

moved {
  from = azuread_application.server
  to   = module.environment.azuread_application.server
}

moved {
  from = azuread_service_principal.server
  to   = module.environment.azuread_service_principal.server
}

moved {
  from = azuread_application_password.server
  to   = module.environment.azuread_application_password.server
}

moved {
  from = azurerm_role_assignment.server_email
  to   = module.environment.azurerm_role_assignment.server_email
}

# ── .env.local ────────────────────────────────────────────────────────────────
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
    # kinotic-server Azure identity: the ${var.project}-${var.environment}-server service principal, created by
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
  triggers_replace = [module.environment.server_client_secret_key_id, local.env_local_script]

  provisioner "local-exec" {
    command = local.env_local_script
    environment = {
      AZURE_CLIENT_ID     = module.environment.server_client_id
      AZURE_CLIENT_SECRET = module.environment.server_client_secret
      AZURE_TENANT_ID     = module.environment.tenant_id
    }
  }
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

variable "lets_encrypt_email" {
  description = "Account email for the Let's Encrypt registration that issues the sites wildcard certificate; set it in local.auto.tfvars"
  type        = string
}

# ── Outputs ─────────────────────────────────────────────────────────────────

output "sites_domain" {
  description = "The domain every published UI is a label under"
  value       = module.environment.sites_domain
}

output "server_client_id" {
  description = "The service principal kinotic-server runs as; its credentials are in .env.local at the repository root"
  value       = module.environment.server_client_id
}

output "application_local_yml" {
  description = "The `local` profile kinotic-server runs with: write it to kinotic-server/src/main/resources/application-local.yml"
  value       = <<-EOT
    kinotic:
      systemApi:
        uiDeployment:
          disableProvisioner: false
          sitesDomain: ${module.environment.sites_domain}
          sitesStorageEndpoint: ${module.environment.sites_storage_blob_endpoint}
  EOT
}
