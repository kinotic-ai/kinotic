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

# ── Platform Key Vault access ─────────────────────────────────────────────────
# Read-only access to the global platform Key Vault (JWT signing keys, secret-storage
# master keys). The global vault lives in rg-kinotic-global and is provisioned by the
# global/ terraform stack; we reach its id via terraform_remote_state.

resource "azurerm_role_assignment" "kinotic_server_platform_kv" {
  scope                = local.global.platform_key_vault_id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.kinotic_server.principal_id
}

# ── Organization storage access ───────────────────────────────────────────────
# kinotic-server provisions one storage account per organization at runtime
# (AzureOrganizationStorageProvisioner) in the org-storage resource group: the account
# and its ui container through the management plane, its private endpoint in the
# private-endpoints subnet, and the endpoint's registration in the private DNS zone.
# It then signs upload URLs and manages blobs in every account through the data plane.

resource "azurerm_role_assignment" "kinotic_server_org_storage_accounts" {
  scope                = azurerm_resource_group.org_storage.id
  role_definition_name = "Storage Account Contributor"
  principal_id         = azurerm_user_assigned_identity.kinotic_server.principal_id
}

resource "azurerm_role_assignment" "kinotic_server_org_storage_blobs" {
  scope                = azurerm_resource_group.org_storage.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azurerm_user_assigned_identity.kinotic_server.principal_id
}

resource "azurerm_role_assignment" "kinotic_server_org_storage_network" {
  scope                = azurerm_resource_group.org_storage.id
  role_definition_name = "Network Contributor"
  principal_id         = azurerm_user_assigned_identity.kinotic_server.principal_id
}

# Placing a private endpoint joins the subnet, which lives in the main resource group
resource "azurerm_role_assignment" "kinotic_server_private_endpoint_subnet" {
  scope                = azurerm_subnet.private_endpoints.id
  role_definition_name = "Network Contributor"
  principal_id         = azurerm_user_assigned_identity.kinotic_server.principal_id
}

resource "azurerm_role_assignment" "kinotic_server_blob_private_dns" {
  scope                = azurerm_private_dns_zone.blob.id
  role_definition_name = "Private DNS Zone Contributor"
  principal_id         = azurerm_user_assigned_identity.kinotic_server.principal_id
}

# ── UI sites access ───────────────────────────────────────────────────────────
# kinotic-server serves each published UI through the shared Front Door profile
# (FrontDoorUiDeploymentProvisioner): per organization an origin group, origin and rule
# set, per site a custom domain and route, and per site a CNAME and validation TXT in
# the platform DNS zone.

resource "azurerm_role_assignment" "kinotic_server_frontdoor" {
  scope                = azurerm_cdn_frontdoor_profile.sites.id
  role_definition_name = "CDN Profile Contributor"
  principal_id         = azurerm_user_assigned_identity.kinotic_server.principal_id
}

resource "azurerm_role_assignment" "kinotic_server_sites_dns" {
  scope                = local.global.dns_zone_id
  role_definition_name = "DNS Zone Contributor"
  principal_id         = azurerm_user_assigned_identity.kinotic_server.principal_id
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
