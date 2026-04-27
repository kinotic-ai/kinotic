# ── Platform Key Vault ────────────────────────────────────────────────────────
# Holds platform-wide secrets (JWT signing keys, secret-storage master keys) that every
# kinotic cluster consumes via the Secrets Store CSI driver. Rotation is performed
# out-of-band (`az keyvault secret set ...`); terraform intentionally does not manage the
# secret values after initial seeding — hence `lifecycle.ignore_changes = [value]`.

resource "azurerm_key_vault" "platform" {
  name                       = "kv-${var.project}-platform"
  location                   = azurerm_resource_group.global.location
  resource_group_name        = azurerm_resource_group.global.name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  soft_delete_retention_days = 90
  purge_protection_enabled   = true
  rbac_authorization_enabled = true

  tags = local.common_tags
}

# ── Terraform principal access ────────────────────────────────────────────────
# Vault uses RBAC, so the terraform caller needs an explicit data-plane role to
# manage secrets. Without this the apply 403s on the very first secret check.

resource "azurerm_role_assignment" "platform_kv_tf_secrets_officer" {
  scope                = azurerm_key_vault.platform.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

# Wait for RBAC propagation before the provider hits the data plane.
resource "terraform_data" "wait_for_kv_rbac" {
  input      = azurerm_role_assignment.platform_kv_tf_secrets_officer.id
  provisioner "local-exec" {
    command = "sleep 60"
  }
  depends_on = [azurerm_role_assignment.platform_kv_tf_secrets_officer]
}

# ── Initial key material ──────────────────────────────────────────────────────
# 32 random bytes each, base64-standard encoded. Consumed by the Java side as raw bytes
# (Base64.getDecoder().decode(...)). `b64_std` attribute produces padded base64 which our
# VersionedKeySet parser accepts.

resource "random_id" "jwt_signing_key_v1" {
  byte_length = 32
}

resource "random_id" "secret_storage_master_key_v1" {
  byte_length = 32
}

# ── Secrets ───────────────────────────────────────────────────────────────────
# VersionedKeySet JSON documents. Adding a new version later = az cli update to add `v2`
# and flip `activeKeyId`; terraform does not revisit the value because of ignore_changes.

resource "azurerm_key_vault_secret" "jwt_signing_keys" {
  name         = "kinotic-jwt-signing-keys"
  key_vault_id = azurerm_key_vault.platform.id
  content_type = "application/json"
  value = jsonencode({
    activeKeyId = "v1"
    keys = [
      { id = "v1", key = random_id.jwt_signing_key_v1.b64_std },
    ]
  })

  tags = local.common_tags

  lifecycle {
    ignore_changes = [value]
  }

  depends_on = [terraform_data.wait_for_kv_rbac]
}

resource "azurerm_key_vault_secret" "secret_storage_master_keys" {
  name         = "kinotic-secret-storage-master-keys"
  key_vault_id = azurerm_key_vault.platform.id
  content_type = "application/json"
  value = jsonencode({
    activeKeyId = "v1"
    keys = [
      { id = "v1", key = random_id.secret_storage_master_key_v1.b64_std },
    ]
  })

  tags = local.common_tags

  lifecycle {
    ignore_changes = [value]
  }

  depends_on = [terraform_data.wait_for_kv_rbac]
}

# ── OIDC client secrets ───────────────────────────────────────────────────────
# Stored at name = configId so the helm chart's oidcSecrets.objects[] can mount each as
# /etc/kinotic/oidc-client-secrets/<configId>. PlatformOidcBootstrap reads them on startup.

resource "azurerm_key_vault_secret" "entra_platform_client_secret" {
  name         = "entra-platform"
  key_vault_id = azurerm_key_vault.platform.id
  content_type = "OIDC client secret for kinotic-platform Entra app"
  value        = azuread_application_password.kinotic_platform.value

  tags = local.common_tags

  depends_on = [terraform_data.wait_for_kv_rbac]
}

# ── Outputs ───────────────────────────────────────────────────────────────────
# Consumed by cluster/ terraform via terraform_remote_state to grant read access to the
# kinotic-server managed identity and to pass vault coordinates into the helm chart.

output "platform_key_vault_id" {
  description = "Resource ID of the platform Key Vault"
  value       = azurerm_key_vault.platform.id
}

output "platform_key_vault_uri" {
  description = "URI of the platform Key Vault"
  value       = azurerm_key_vault.platform.vault_uri
}

output "platform_key_vault_name" {
  description = "Name of the platform Key Vault (used by SecretProviderClass in helm)"
  value       = azurerm_key_vault.platform.name
}
