# ── RBAC: kinotic-server can send email ───────────────────────────────────────
# The email infrastructure (ACS, email service, domain, DNS, verification) lives
# in the global terraform — see global/email.tf. Each cluster grants its own
# kinotic-server identity send permission on the shared communication service.

resource "azurerm_role_assignment" "kinotic_server_email_contributor" {
  scope                = local.global.email_communication_service_id
  role_definition_name = "Contributor"
  principal_id         = azurerm_user_assigned_identity.kinotic_server.principal_id
}
