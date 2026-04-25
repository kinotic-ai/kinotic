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
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }

  backend "azurerm" {
    resource_group_name  = "rg-kinotic-tfstate"
    storage_account_name = "stkinotictfstate"
    container_name       = "tfstate"
    key                  = "global/terraform.tfstate"
  }
}

provider "azurerm" {
  features {
    resource_group {
      prevent_deletion_if_contains_resources = true
    }
    key_vault {
      purge_soft_delete_on_destroy    = false
      recover_soft_deleted_key_vaults = true
    }
  }
}

# ── Variables ─────────────────────────────────────────────────────────────────

variable "project" {
  description = "Project name"
  type        = string
  default     = "kinotic"
}

variable "location" {
  description = "Azure region for the global resource group"
  type        = string
  default     = "centralus"
}

variable "domain_name" {
  description = "Domain name for the DNS zone"
  type        = string
  default     = "kinotic.ai"
}

variable "disable_grafana_entra" {
  description = "Disable Entra ID login for Grafana"
  type        = bool
  default     = false
}

data "azurerm_client_config" "current" {}

locals {
  common_tags = {
    project    = var.project
    managed_by = "terraform"
    scope      = "global"
  }
}

# ── Resource Group ────────────────────────────────────────────────────────────

resource "azurerm_resource_group" "global" {
  name     = "rg-${var.project}-global"
  location = var.location
  tags     = local.common_tags
}

# ── DNS Zone ──────────────────────────────────────────────────────────────────

resource "azurerm_dns_zone" "main" {
  name                = var.domain_name
  resource_group_name = azurerm_resource_group.global.name
  tags                = local.common_tags
}

# ── Kinotic Platform OIDC App Registration ────────────────────────────────────

resource "azuread_application" "kinotic_platform" {
  display_name     = "${var.project}-platform"
  sign_in_audience = "AzureADMyOrg"

  web {
    redirect_uris = [
      "https://portal.${var.domain_name}/login",
      "https://portal.${var.domain_name}/silent-renew.html",
    ]
    implicit_grant {
      access_token_issuance_enabled = false
      id_token_issuance_enabled     = true
    }
  }

  single_page_application {
    redirect_uris = [
      "https://portal.${var.domain_name}/login",
      "https://portal.${var.domain_name}/silent-renew.html",
    ]
  }

  required_resource_access {
    resource_app_id = "00000003-0000-0000-c000-000000000000"
    resource_access {
      id   = "e1fe6dd8-ba31-4d61-89e7-88639da4683d"
      type = "Scope"
    }
    resource_access {
      id   = "64a6cdd6-aab1-4aaf-94b8-3cc8405e90d0"
      type = "Scope"
    }
    resource_access {
      id   = "37f7f235-527c-4136-accd-4a02d197296e"
      type = "Scope"
    }
    resource_access {
      id   = "14dad69e-099b-42c9-810b-d002981feec1"
      type = "Scope"
    }
  }

  optional_claims {
    id_token {
      name = "email"
    }
    id_token {
      name = "preferred_username"
    }
  }
}

resource "azuread_service_principal" "kinotic_platform" {
  client_id = azuread_application.kinotic_platform.client_id
}

# Create kinoticTenantId directory extension via Graph API
resource "terraform_data" "create_tenant_id_extension" {
  provisioner "local-exec" {
    command = <<-EOT
      set -e
      APP_OBJECT_ID="${azuread_application.kinotic_platform.object_id}"
      EXISTING=$(az rest --method GET \
        --url "https://graph.microsoft.com/v1.0/applications/$APP_OBJECT_ID/extensionProperties" \
        --query "value[?contains(name,'kinoticTenantId')].name" \
        -o tsv 2>/dev/null || true)
      if [ -n "$EXISTING" ]; then
        echo "Extension property kinoticTenantId already exists"
        exit 0
      fi
      echo "Creating extension property kinoticTenantId..."
      az rest --method POST \
        --url "https://graph.microsoft.com/v1.0/applications/$APP_OBJECT_ID/extensionProperties" \
        --body '{"name": "kinoticTenantId", "dataType": "String", "targetObjects": ["User"]}'
      echo "Extension property created"
    EOT
  }
  depends_on = [azuread_service_principal.kinotic_platform]
}

# ── Grafana Entra ID App Registration (conditional) ──────────────────────────

resource "azuread_application" "grafana" {
  count        = !var.disable_grafana_entra ? 1 : 0
  display_name = "${var.project}-grafana"

  web {
    redirect_uris = [
      "https://grafana.${var.domain_name}/login/generic_oauth",
      "https://grafana.${var.domain_name}/login/azuread",
    ]
    implicit_grant {
      access_token_issuance_enabled = false
      id_token_issuance_enabled     = true
    }
  }

  required_resource_access {
    resource_app_id = "00000003-0000-0000-c000-000000000000"
    resource_access {
      id   = "e1fe6dd8-ba31-4d61-89e7-88639da4683d"
      type = "Scope"
    }
    resource_access {
      id   = "64a6cdd6-aab1-4aaf-94b8-3cc8405e90d0"
      type = "Scope"
    }
    resource_access {
      id   = "37f7f235-527c-4136-accd-4a02d197296e"
      type = "Scope"
    }
    resource_access {
      id   = "14dad69e-099b-42c9-810b-d002981feec1"
      type = "Scope"
    }
  }

  optional_claims {
    id_token {
      name = "email"
    }
    id_token {
      name = "preferred_username"
    }
  }
}

resource "azuread_application_password" "grafana" {
  count          = !var.disable_grafana_entra ? 1 : 0
  application_id = azuread_application.grafana[0].id
  display_name   = "grafana-terraform"
}

# ── Outputs ───────────────────────────────────────────────────────────────────
# These are consumed by the cluster terraform via terraform_remote_state

output "resource_group_name" {
  value = azurerm_resource_group.global.name
}

output "dns_zone_name" {
  value = azurerm_dns_zone.main.name
}

output "dns_zone_id" {
  value = azurerm_dns_zone.main.id
}

output "dns_nameservers" {
  value = azurerm_dns_zone.main.name_servers
}

output "tenant_id" {
  value = data.azurerm_client_config.current.tenant_id
}

output "subscription_id" {
  value = data.azurerm_client_config.current.subscription_id
}

output "kinotic_oidc_client_id" {
  value = azuread_application.kinotic_platform.client_id
}

output "kinotic_oidc_authority" {
  value = "https://login.microsoftonline.com/${data.azurerm_client_config.current.tenant_id}/v2.0"
}

output "kinotic_oidc_tenant_id_claim" {
  value = "extension_${replace(azuread_application.kinotic_platform.client_id, "-", "")}_kinoticTenantId"
}

output "grafana_entra_client_id" {
  value = !var.disable_grafana_entra ? azuread_application.grafana[0].client_id : ""
}

output "grafana_entra_client_secret" {
  value     = !var.disable_grafana_entra ? azuread_application_password.grafana[0].value : ""
  sensitive = true
}

output "set_user_tenant_id" {
  value = "az rest --method PATCH --url 'https://graph.microsoft.com/v1.0/users/<user-object-id>' --body '{\"extension_${replace(azuread_application.kinotic_platform.client_id, "-", "")}_kinoticTenantId\": \"<tenant-value>\"}'"
}
