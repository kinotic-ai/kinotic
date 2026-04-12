# ── Azure Key Vault ───────────────────────────────────────────────────────────
# Stores system and customer secrets. kinotic-server pods access via
# workload identity — no secret credentials needed in K8s.

data "azurerm_client_config" "keyvault" {}

resource "azurerm_key_vault" "main" {
  name                       = "kv-${local.name_prefix}"
  location                   = var.location
  resource_group_name        = azurerm_resource_group.main.name
  tenant_id                  = data.azurerm_client_config.keyvault.tenant_id
  sku_name                   = "standard"
  soft_delete_retention_days = 7
  purge_protection_enabled   = false  # set true for production

  # Use RBAC for access control (not access policies)
  rbac_authorization_enabled = true

  tags = local.common_tags
}

# ── Managed Identity for kinotic-server ───────────────────────────────────────

resource "azurerm_user_assigned_identity" "kinotic_server" {
  name                = "id-${local.name_prefix}-kinotic-server"
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags
}

# Key Vault Secrets Officer — read, write, list, delete secrets
resource "azurerm_role_assignment" "kinotic_server_kv_secrets" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = azurerm_user_assigned_identity.kinotic_server.principal_id
}

# Federated credential so kinotic-server pods authenticate via workload identity
resource "azurerm_federated_identity_credential" "kinotic_server" {
  name                  = "kinotic-server-federated"
  user_assigned_identity_id = azurerm_user_assigned_identity.kinotic_server.id
  audience              = ["api://AzureADTokenExchange"]
  issuer                = data.azurerm_kubernetes_cluster.main.oidc_issuer_url
  subject               = "system:serviceaccount:kinotic:kinotic-server"
}

# ── Outputs ───────────────────────────────────────────────────────────────────

output "key_vault_url" {
  description = "Azure Key Vault URL for kinotic-server"
  value       = azurerm_key_vault.main.vault_uri
}

output "kinotic_server_identity_client_id" {
  description = "Client ID for kinotic-server workload identity"
  value       = azurerm_user_assigned_identity.kinotic_server.client_id
}
