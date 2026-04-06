# ── Kinotic Platform OIDC App Registration ────────────────────────────────────
# Enables Entra ID (Azure AD) authentication for the kinotic platform.
# Creates a directory extension attribute (kinoticTenantId) for multi-tenant
# data isolation. Set per user via the Azure Portal or Graph API.
#
# After deploy, use the terraform output command to assign tenants:
#   terraform output set_user_tenant_id

data "azurerm_client_config" "auth" {}

resource "azuread_application" "kinotic_platform" {
  display_name = "${local.name_prefix}-platform"

  # Single-tenant by default — set to "AzureADMultipleOrgs" for multi-tenant
  sign_in_audience = "AzureADMyOrg"

  web {
    redirect_uris = [
      "https://${var.domain_name}/login",
      "https://${var.domain_name}/silent-renew.html",
    ]

    implicit_grant {
      access_token_issuance_enabled = false
      id_token_issuance_enabled     = true
    }
  }

  # SPA redirect URIs (for browser-based OIDC flows)
  single_page_application {
    redirect_uris = [
      "https://${var.domain_name}/login",
      "https://${var.domain_name}/silent-renew.html",
    ]
  }

  required_resource_access {
    # Microsoft Graph
    resource_app_id = "00000003-0000-0000-c000-000000000000"

    resource_access {
      # User.Read
      id   = "e1fe6dd8-ba31-4d61-89e7-88639da4683d"
      type = "Scope"
    }
    resource_access {
      # email
      id   = "64a6cdd6-aab1-4aaf-94b8-3cc8405e90d0"
      type = "Scope"
    }
    resource_access {
      # openid
      id   = "37f7f235-527c-4136-accd-4a02d197296e"
      type = "Scope"
    }
    resource_access {
      # profile
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

# ── Service Principal (required for directory extensions) ─────────────────────

resource "azuread_service_principal" "kinotic_platform" {
  client_id = azuread_application.kinotic_platform.client_id
}

# ── Directory Extension: tenantId ─────────────────────────────────────────────
# Creates a custom attribute on the app registration that can be set per user.
# The attribute appears in tokens as "extension_<appObjectId>_tenantId".
# Set per user via Graph API or Azure Portal.

resource "azuread_application_extension_property" "tenant_id" {
  application_object_id = azuread_application.kinotic_platform.object_id
  name                  = "kinoticTenantId"
  data_type             = "String"
  target_objects        = ["User"]

  depends_on = [azuread_service_principal.kinotic_platform]
}

# ── Outputs ───────────────────────────────────────────────────────────────────

output "kinotic_oidc_client_id" {
  description = "Client ID for kinotic platform OIDC — use in frontend config"
  value       = azuread_application.kinotic_platform.client_id
}

output "kinotic_oidc_authority" {
  description = "OIDC authority URL"
  value       = "https://login.microsoftonline.com/${data.azurerm_client_config.auth.tenant_id}/v2.0"
}

output "kinotic_oidc_tenant_id_claim" {
  description = "The claim name to use for tenantIdFieldName in kinotic config"
  value       = azuread_application_extension_property.tenant_id.name
}

output "set_user_tenant_id" {
  description = "Command to set a user's tenantId (replace <user-id> and <tenant-value>)"
  value       = "az rest --method PATCH --url 'https://graph.microsoft.com/v1.0/users/<user-id>' --body '{\"${azuread_application_extension_property.tenant_id.name}\": \"<tenant-value>\"}'"
}
