# ── System console sign-in ───────────────────────────────────────────────────
# System users sign in to the system console with their Minds Ignited account, through a single-tenant
# registration only this tenant's accounts can use. The system server reads it from a SYSTEM row of
# kinotic_oidc_configuration, which proxmox/bootstrap-system-sign-in.sh writes from the output below, and its
# client secret from the servers' key vault by the row's secretNameRef.

locals {
  # the row's id, the last segment of its redirect URI, and the name of its secret in the vault
  system_sign_in_id = "entra-system"
}

resource "azuread_application" "system_console" {
  display_name     = "${var.project}-${var.environment}-system-console"
  sign_in_audience = "AzureADMyOrg"

  api {
    requested_access_token_version = 2
  }

  web {
    redirect_uris = ["https://${local.system_api_hostname}/api/auth/system/login/oidc/callback/${local.system_sign_in_id}"]
    implicit_grant {
      access_token_issuance_enabled = false
      id_token_issuance_enabled     = false
    }
  }

  # Microsoft Graph's delegated openid, email and profile
  required_resource_access {
    resource_app_id = "00000003-0000-0000-c000-000000000000"
    resource_access {
      id   = "37f7f235-527c-4136-accd-4a02d197296e"
      type = "Scope"
    }
    resource_access {
      id   = "64a6cdd6-aab1-4aaf-94b8-3cc8405e90d0"
      type = "Scope"
    }
    resource_access {
      id   = "14dad69e-099b-42c9-810b-d002981feec1"
      type = "Scope"
    }
  }

  # Entra puts the address in the id token only when asked; the server admits system users by its domain
  optional_claims {
    id_token {
      name = "email"
    }
  }
}

resource "azuread_service_principal" "system_console" {
  client_id = azuread_application.system_console.client_id
}

resource "azuread_application_password" "system_console" {
  application_id = azuread_application.system_console.id
  display_name   = "${var.project}-${var.environment}-system-console"
}

resource "azurerm_key_vault_secret" "system_console" {
  name         = local.system_sign_in_id
  value        = azuread_application_password.system_console.value
  key_vault_id = azurerm_key_vault.server.id

  depends_on = [azurerm_role_assignment.operator_key_vault_secrets]
}

# What proxmox/bootstrap-system-sign-in.sh writes as the SYSTEM row. Nothing here is secret.
output "system_sign_in" {
  description = "The system console's sign-in registration, as the fields of its kinotic_oidc_configuration row"
  value = {
    id                 = local.system_sign_in_id
    name               = "Microsoft"
    provider           = "azure-ad"
    clientId           = azuread_application.system_console.client_id
    secretNameRef      = azurerm_key_vault_secret.system_console.name
    authority          = "https://login.microsoftonline.com/${module.environment.tenant_id}/v2.0"
    allowedEmailDomain = "mindsignited.com"
  }
}
