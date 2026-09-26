# ── Organization storage ──────────────────────────────────────────────────────
# The files the platform keeps on behalf of organizations, partitioned by organization and then
# by use, which workloads write and the portal reads through URLs kinotic-server issues.

module "organizations_storage" {
  source = "../modules/organizations-storage"

  name_prefix         = local.name_prefix
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags
  portal_origins      = [local.portal_url]
  server_principal_id = azurerm_user_assigned_identity.kinotic_server.principal_id
}
