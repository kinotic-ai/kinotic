# ── Developer UI publishing ───────────────────────────────────────────────────
# What the system server running on a developer machine needs to publish UIs for real: the
# dev-environment module (a resource group, the sites storage account, the sites Worker and
# domain <environment>.<cloudflare zone>, and a service principal with the roles the servers
# need), plus the lines in .env.local that make the servers on this machine run as that
# principal and create organizations' sites records.
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
    # The sites module: origin authentication at an API version azurerm lacks
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

  project     = var.project
  environment = var.environment
  location    = var.location
  tags        = local.common_tags

  dns_zone_name                  = local.global.dns_zone_name
  dns_zone_id                    = local.global.dns_zone_id
  dns_zone_resource_group_name   = local.global.resource_group_name
  email_communication_service_id = local.global.email_communication_service_id

  cloudflare_zone_name  = var.cloudflare_zone_name
  cloudflare_zone_id    = var.cloudflare_zone_id
  cloudflare_account_id = var.cloudflare_account_id
  # a developer's app server is on their machine, so the published sites have no reachable API
  app_server_address = null
  portal_base_url    = "http://localhost:5173"
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
      skip && /^KINOTIC_DOMAIN_SITEDOMAIN_CLOUDFLARE_APITOKEN=/ { skip = 0; next }
      skip { next }
      /^AZURE_(CLIENT_ID|CLIENT_SECRET|TENANT_ID)=/ { next }
      /^KINOTIC_DOMAIN_SITEDOMAIN_CLOUDFLARE_APITOKEN=/ { next }
      { lines[++n] = $0; if ($0 !~ /^[[:space:]]*$/) last = n }
      END { for (i = 1; i <= last; i++) print lines[i] }
    ' "$f" > "$f.tmp"
    if [ -s "$f.tmp" ]; then printf '\n' >> "$f.tmp"; fi
    cat >> "$f.tmp" <<EOF
    # kinotic-server Azure identity: the ${var.project}-${var.environment}-server service principal, created by
    # deployment/terraform/azure/dev with the roles the server needs to publish UIs and send email,
    # and the Cloudflare token the server creates organizations' sites records with.
    # DefaultAzureCredential reads these before anything else. Written by terraform apply there;
    # to write them again: terraform apply -replace=terraform_data.env_local
    AZURE_CLIENT_ID=$AZURE_CLIENT_ID
    AZURE_CLIENT_SECRET=$AZURE_CLIENT_SECRET
    AZURE_TENANT_ID=$AZURE_TENANT_ID
    KINOTIC_DOMAIN_SITEDOMAIN_CLOUDFLARE_APITOKEN=$CLOUDFLARE_DNS_API_TOKEN
    EOF
    mv "$f.tmp" "$f"
  EOT
}

resource "terraform_data" "env_local" {
  triggers_replace = [module.environment.server_client_secret_key_id, local.env_local_script]

  provisioner "local-exec" {
    command = local.env_local_script
    environment = {
      AZURE_CLIENT_ID          = module.environment.server_client_id
      AZURE_CLIENT_SECRET      = module.environment.server_client_secret
      AZURE_TENANT_ID          = module.environment.tenant_id
      CLOUDFLARE_DNS_API_TOKEN = var.cloudflare_dns_api_token
    }
  }
}

# ── Variables ─────────────────────────────────────────────────────────────────

variable "environment" {
  description = "Names everything this root creates and the <environment>.<cloudflare zone> sites domain; one per developer"
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

variable "cloudflare_zone_name" {
  description = "The Cloudflare zone the sites domain is a label under"
  type        = string
  default     = "kinotic.io"
}

variable "cloudflare_zone_id" {
  description = "The zone's id; set it in local.auto.tfvars"
  type        = string
}

variable "cloudflare_account_id" {
  description = "The Cloudflare account the sites Worker is deployed in; set it in local.auto.tfvars"
  type        = string
}

variable "cloudflare_api_token" {
  description = "The token this root applies the zone with: Workers Scripts edit on the account, DNS and zone settings edit on the zone; set it in local.auto.tfvars"
  type        = string
  sensitive   = true
}

variable "cloudflare_dns_api_token" {
  description = "A token holding DNS edit on the zone and nothing else, written to .env.local for the servers to create organizations' sites records with; set it in local.auto.tfvars"
  type        = string
  sensitive   = true
}

# ── Outputs ─────────────────────────────────────────────────────────────────

output "sites_domain" {
  description = "The domain every published UI is a label under"
  value       = module.environment.sites_domain
}

output "server_client_id" {
  description = "The service principal the servers on a developer machine run as; its credentials are in .env.local at the repository root"
  value       = module.environment.server_client_id
}

output "app_server_application_local_yml" {
  description = "The `local` profile kinotic-server-app runs with, naming the sites domain its logins admit pages from: write it to kinotic-server-app/src/main/resources/application-local.yml"
  value       = <<-EOT
    kinotic:
      domain:
        siteDomain:
          domain: ${module.environment.sites_domain}
  EOT
}

output "application_local_yml" {
  description = "The `local` profile kinotic-server-system runs with: write it to kinotic-server-system/src/main/resources/application-local.yml"
  value       = <<-EOT
    kinotic:
      domain:
        siteDomain:
          domain: ${module.environment.sites_domain}
          cloudflare:
            enabled: true
            zoneId: ${var.cloudflare_zone_id}
      systemApi:
        disableAzureStorage: false
        uiDeployment:
          sitesDomain: ${module.environment.sites_domain}
          sitesStorageEndpoint: ${module.environment.sites_storage_blob_endpoint}
  EOT
}
