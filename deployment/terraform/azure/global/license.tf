# ── Kinotic Enterprise License signing key ────────────────────────────────────
# The EC P-256 key that signs Kinotic Enterprise License keys with ES256. Key Vault generates it
# and keeps the private key: a license key is issued by calling the vault's sign operation, and
# the private key cannot be exported. The public key is the one Kinotic's license verifier
# trusts, read from the license_signing_public_key_pem output. The servers' identity holds only
# Key Vault Secrets User on this vault, which cannot sign.

resource "azurerm_role_assignment" "platform_kv_tf_crypto_officer" {
  scope                = azurerm_key_vault.platform.id
  role_definition_name = "Key Vault Crypto Officer"
  principal_id         = data.azuread_group.terraform_operators.object_id
  principal_type       = "Group"
}

# Wait for RBAC propagation before the provider creates the key.
resource "terraform_data" "wait_for_kv_crypto_rbac" {
  input      = azurerm_role_assignment.platform_kv_tf_crypto_officer.id
  provisioner "local-exec" {
    command = "sleep 60"
  }
  depends_on = [azurerm_role_assignment.platform_kv_tf_crypto_officer]
}

resource "azurerm_key_vault_key" "license_signing" {
  name         = "kinotic-license-signing"
  key_vault_id = azurerm_key_vault.platform.id
  key_type     = "EC"
  curve        = "P-256"
  key_opts     = ["sign", "verify"]

  tags = local.common_tags

  # Every license key issued is verified against this key's public key, so replacing it
  # invalidates every license key in the field
  lifecycle {
    prevent_destroy = true
  }

  depends_on = [terraform_data.wait_for_kv_crypto_rbac]
}

output "license_signing_public_key_pem" {
  description = "Public key of the Kinotic Enterprise License signing key, which Kinotic's license verifier trusts"
  value       = azurerm_key_vault_key.license_signing.public_key_pem
}

# The license key the servers once imported as a property is forgotten, not destroyed: the cluster's key is
# installed on the system console's License page, and this copy stays in the vault to install on a rebuilt cluster
removed {
  from = azurerm_key_vault_secret.ee_license_key

  lifecycle {
    destroy = false
  }
}
