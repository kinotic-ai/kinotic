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
  display_name = "${var.project}-platform"
  # Allow any Microsoft account — work, school, or personal (outlook.com, live.com, hotmail.com).
  # This is the "Continue with Microsoft" social-provider experience: anyone with a Microsoft
  # identity can sign up. Pair with authority "/common/v2.0" so Entra routes the user to the
  # right home tenant during sign-in. Personal accounts require v2 tokens (api block below).
  sign_in_audience = "AzureADandPersonalMicrosoftAccount"

  # Required when sign_in_audience accepts personal accounts. v2 also gives modern claim
  # shapes (oid, sub, email under "email" claim, etc.) that the rest of the OIDC code expects.
  api {
    requested_access_token_version = 2
  }

  # Web-platform redirect URIs — used by the server-side OIDC flow. Login and signup
  # are deliberately separate handlers (LoginHandler / OidcSignupHandler), so each needs
  # its own callback path. Microsoft permits http://localhost:* for dev without HTTPS.
  # The portal.* URI is for production; the others cover bare-local Java (9090), the Vite
  # dev server (5173), and KinD with mkcert (https://localhost). The configId in the path
  # is "entra-platform" to match kinotic.oidc.platformProviders[0].id.
  web {
    redirect_uris = [
      "https://portal.${var.domain_name}/api/login/callback/entra-platform",
      "https://portal.${var.domain_name}/api/signup/callback/entra-platform",
      "http://localhost:9090/api/login/callback/entra-platform",
      "http://localhost:9090/api/signup/callback/entra-platform",
      "http://localhost:5173/api/login/callback/entra-platform",
      "http://localhost:5173/api/signup/callback/entra-platform",
      "https://localhost/api/login/callback/entra-platform",
      "https://localhost/api/signup/callback/entra-platform",
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

resource "azuread_service_principal" "kinotic_platform" {
  client_id = azuread_application.kinotic_platform.client_id
}

# OAuth2 client secret for the platform Entra app. Consumed by the kinotic-server
# OIDC bootstrap as the "entra-platform" config's clientSecret.
resource "azuread_application_password" "kinotic_platform" {
  application_id = azuread_application.kinotic_platform.id
  display_name   = "kinotic-platform-oidc"
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
  # /common/v2.0 — accepts any Microsoft identity (work, school, personal). Pairs with
  # azuread_application.kinotic_platform.sign_in_audience = AzureADandPersonalMicrosoftAccount.
  # If you ever need to lock back down to a single tenant, swap this to
  # "https://login.microsoftonline.com/${data.azurerm_client_config.current.tenant_id}/v2.0"
  # and set sign_in_audience back to "AzureADMyOrg".
  value = "https://login.microsoftonline.com/common/v2.0"
}

# Sensitive — used to populate the platform OIDC client secret for local/KinD dev when
# you don't want to round-trip through Key Vault. Pull with:
#   terraform -chdir=deployment/terraform/azure/global output -raw kinotic_oidc_entra_platform_client_secret
output "kinotic_oidc_entra_platform_client_secret" {
  description = "Client secret for the kinotic-platform Entra app (OIDC configId: entra-platform)"
  value       = azuread_application_password.kinotic_platform.value
  sensitive   = true
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
