# ── System console sign-in ───────────────────────────────────────────────────
# System users sign in to the system console with their Minds Ignited account, through a single-tenant
# registration only this tenant's accounts can use. A system user adds it in the console under
# Sign-in providers, as a Microsoft Entra ID provider, with the client id, directory id and secret output
# below; the console then shows the callback URL, whose last segment is the provider's id, which goes
# in var.system_sign_in_provider_id so the registration lists that URL.

resource "azuread_application" "system_console" {
  display_name     = "${var.project}-${var.environment}-system-console"
  sign_in_audience = "AzureADMyOrg"

  api {
    requested_access_token_version = 2
  }

  web {
    redirect_uris = var.system_sign_in_provider_id == "" ? [] : ["https://${local.system_api_hostname}/api/auth/system/login/oidc/callback/${var.system_sign_in_provider_id}"]
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

  # Entra puts the address in the id token only when asked; a system user is first signed in by it
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

# What a system user enters in the console's Sign-in providers page for this registration
output "system_sign_in_client_id" {
  description = "The client ID of the system console's Entra registration"
  value       = azuread_application.system_console.client_id
}

output "system_sign_in_directory_id" {
  description = "The directory (tenant) the registration's accounts are in, the provider's account in the console"
  value       = module.environment.tenant_id
}

output "system_sign_in_client_secret" {
  description = "The client secret of the system console's Entra registration"
  value       = azuread_application_password.system_console.value
  sensitive   = true
}
