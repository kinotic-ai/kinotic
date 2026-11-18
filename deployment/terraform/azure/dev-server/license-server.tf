# ── The license key server ───────────────────────────────────────────────────
# The development server also runs Minds Ignited's license key server (kinotic-ai/license-key-server)
# at the names Kinotic connects to for keys: licenses.kinotic.ai for customers and the clusters
# renewing their keys, licenses-console.kinotic.ai for staff. It runs as a principal of its own, so
# the Kinotic servers' principal never holds the right to sign license keys, and staff sign in
# through a single-tenant registration that only this tenant's accounts can use.

locals {
  licenses_hostname         = "licenses.${local.global.dns_zone_name}"
  licenses_console_hostname = "licenses-console.${local.global.dns_zone_name}"
}

data "azurerm_key_vault_key" "license_signing" {
  name         = "kinotic-license-signing"
  key_vault_id = local.global.platform_key_vault_id
}

resource "azuread_application" "license_server" {
  display_name = "${var.project}-${var.environment}-license-server"
}

resource "azuread_service_principal" "license_server" {
  client_id = azuread_application.license_server.client_id
}

resource "azuread_application_password" "license_server" {
  application_id = azuread_application.license_server.id
  display_name   = "${var.project}-${var.environment}-license-server"
}

# Sign and get on the signing key alone: the private key never leaves the vault
resource "azurerm_role_assignment" "license_server_signing" {
  scope                = data.azurerm_key_vault_key.license_signing.resource_versionless_id
  role_definition_name = "Key Vault Crypto User"
  principal_id         = azuread_service_principal.license_server.object_id
  # a principal created moments ago may not have replicated to the RBAC lookup yet
  skip_service_principal_aad_check = true
}

resource "azurerm_role_assignment" "license_server_email" {
  scope                            = local.global.email_communication_service_id
  role_definition_name             = "Contributor"
  principal_id                     = azuread_service_principal.license_server.object_id
  skip_service_principal_aad_check = true
}

# Staff sign in to the console with their Minds Ignited account
resource "azuread_application" "license_console" {
  # what Microsoft's sign-in page names as the application being signed in to
  display_name     = "Kinotic, Inc"
  sign_in_audience = "AzureADMyOrg"

  api {
    requested_access_token_version = 2
  }

  web {
    redirect_uris = ["https://${local.licenses_console_hostname}/api/auth/staff/callback"]
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

  # Entra puts the address in the id token only when asked; the server admits staff by its domain
  optional_claims {
    id_token {
      name = "email"
    }
  }
}

resource "azuread_service_principal" "license_console" {
  client_id = azuread_application.license_console.client_id
}

resource "azuread_application_password" "license_console" {
  application_id = azuread_application.license_console.id
  display_name   = "${var.project}-license-console"
}

# The proxmox root sets this as the license server's environment, with the database's address.
# Nothing here is secret.
output "license_server_env" {
  description = "The non-secret half of the license key server's environment"
  value = {
    KINOTIC_LICENSES_PUBLICBASEURL                = "https://${local.licenses_hostname}"
    KINOTIC_LICENSES_STAFFBASEURL                 = "https://${local.licenses_console_hostname}"
    KINOTIC_LICENSES_SIGNINGKEYID                 = data.azurerm_key_vault_key.license_signing.id
    KINOTIC_LICENSES_EMAIL_ENDPOINT               = local.global.email_service_endpoint
    KINOTIC_LICENSES_EMAIL_SENDERADDRESS          = "DoNotReply@${local.global.email_sender_domain}"
    KINOTIC_LICENSES_STAFFOIDC_ISSUER             = "https://login.microsoftonline.com/${module.environment.tenant_id}/v2.0"
    KINOTIC_LICENSES_STAFFOIDC_CLIENTID           = azuread_application.license_console.client_id
    KINOTIC_LICENSES_STAFFOIDC_ALLOWEDEMAILDOMAIN = "mindsignited.com"
    KINOTIC_CORS_ALLOWEDORIGINPATTERN             = "https://(${replace(local.licenses_hostname, ".", "\\.")}|${replace(local.licenses_console_hostname, ".", "\\.")})"
    AZURE_CLIENT_ID                               = azuread_application.license_server.client_id
    AZURE_TENANT_ID                               = module.environment.tenant_id
  }
}

output "license_server_hostnames" {
  description = "The license key server's public and staff hostnames, which the edge terminates TLS for"
  value       = [local.licenses_hostname, local.licenses_console_hostname]
}

# The operator places this on the host as license-key-server.env, with the database password
output "license_server_secrets_env" {
  description = "The Azure half of the license key server's secrets, as env-file lines"
  value       = "AZURE_CLIENT_SECRET=${azuread_application_password.license_server.value}\nKINOTIC_LICENSES_STAFFOIDC_CLIENTSECRET=${azuread_application_password.license_console.value}\n"
  sensitive   = true
}
