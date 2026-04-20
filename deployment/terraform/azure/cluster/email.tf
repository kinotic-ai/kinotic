# ── Azure Communication Services (Email) ──────────────────────────────────────
# Fully automated: creates ACS, email service, custom domain, all DNS
# verification records (domain, SPF, DKIM, DKIM2), and triggers verification.
# No manual steps after terraform apply.

resource "azurerm_email_communication_service" "main" {
  name                = "ecs-${local.name_prefix}"
  resource_group_name = azurerm_resource_group.main.name
  data_location       = "United States"
  tags                = local.common_tags
}

resource "azurerm_email_communication_service_domain" "kinotic" {
  name              = local.global.dns_zone_name
  email_service_id  = azurerm_email_communication_service.main.id
  domain_management = "CustomerManaged"
  tags              = local.common_tags
}

resource "azurerm_communication_service" "main" {
  name                = "acs-${local.name_prefix}"
  resource_group_name = azurerm_resource_group.main.name
  data_location       = "United States"
  tags                = local.common_tags
}

# Link email domain to communication service
resource "terraform_data" "link_email_domain" {
  provisioner "local-exec" {
    command = <<-EOT
      set -e
      az communication email domain link \
        --communication-service-name "acs-${local.name_prefix}" \
        --resource-group "${azurerm_resource_group.main.name}" \
        --domain-resource-id "${azurerm_email_communication_service_domain.kinotic.id}" 2>/dev/null || true
    EOT
  }
  depends_on = [
    azurerm_communication_service.main,
    azurerm_email_communication_service_domain.kinotic,
  ]
}

# ── DNS Records (all automated from verification_records output) ──────────────

# Domain ownership verification (TXT)
resource "azurerm_dns_txt_record" "email_domain" {
  name                = azurerm_email_communication_service_domain.kinotic.verification_records[0].domain[0].name
  zone_name           = local.global.dns_zone_name
  resource_group_name = local.global.resource_group_name
  ttl                 = azurerm_email_communication_service_domain.kinotic.verification_records[0].domain[0].ttl

  record {
    value = azurerm_email_communication_service_domain.kinotic.verification_records[0].domain[0].value
  }
}

# SPF record (TXT)
resource "azurerm_dns_txt_record" "email_spf" {
  name                = azurerm_email_communication_service_domain.kinotic.verification_records[0].spf[0].name
  zone_name           = local.global.dns_zone_name
  resource_group_name = local.global.resource_group_name
  ttl                 = azurerm_email_communication_service_domain.kinotic.verification_records[0].spf[0].ttl

  record {
    value = azurerm_email_communication_service_domain.kinotic.verification_records[0].spf[0].value
  }
}

# DKIM record (CNAME)
resource "azurerm_dns_cname_record" "email_dkim" {
  name                = azurerm_email_communication_service_domain.kinotic.verification_records[0].dkim[0].name
  zone_name           = local.global.dns_zone_name
  resource_group_name = local.global.resource_group_name
  ttl                 = azurerm_email_communication_service_domain.kinotic.verification_records[0].dkim[0].ttl
  record              = azurerm_email_communication_service_domain.kinotic.verification_records[0].dkim[0].value
}

# DKIM2 record (CNAME)
resource "azurerm_dns_cname_record" "email_dkim2" {
  name                = azurerm_email_communication_service_domain.kinotic.verification_records[0].dkim2[0].name
  zone_name           = local.global.dns_zone_name
  resource_group_name = local.global.resource_group_name
  ttl                 = azurerm_email_communication_service_domain.kinotic.verification_records[0].dkim2[0].ttl
  record              = azurerm_email_communication_service_domain.kinotic.verification_records[0].dkim2[0].value
}

# ── Trigger domain verification ───────────────────────────────────────────────

resource "terraform_data" "verify_email_domain" {
  provisioner "local-exec" {
    command = <<-EOT
      set -e
      DOMAIN_ID="${azurerm_email_communication_service_domain.kinotic.id}"

      echo "Initiating domain verification..."
      for RECORD_TYPE in Domain SPF DKIM DKIM2; do
        echo "  Verifying $RECORD_TYPE..."
        az rest --method POST \
          --url "$DOMAIN_ID/initiateVerification?api-version=2023-04-01" \
          --body "{\"verificationType\": \"$RECORD_TYPE\"}" 2>/dev/null || true
        sleep 5
      done

      echo "Waiting for verification..."
      for i in $(seq 1 30); do
        STATUS=$(az communication email domain show \
          --name "${local.global.dns_zone_name}" \
          --email-service-name "ecs-${local.name_prefix}" \
          --resource-group "${azurerm_resource_group.main.name}" \
          --query "verificationStates.domain.status" -o tsv 2>/dev/null || true)
        if [ "$STATUS" == "Verified" ]; then
          echo "Domain verified!"
          exit 0
        fi
        echo "  Attempt $i/30 — status: $STATUS, waiting 10s..."
        sleep 10
      done
      echo "WARNING: Verification not yet complete. Check: terraform output email_verification_status"
    EOT
  }

  depends_on = [
    azurerm_dns_txt_record.email_domain,
    azurerm_dns_txt_record.email_spf,
    azurerm_dns_cname_record.email_dkim,
    azurerm_dns_cname_record.email_dkim2,
  ]
}

# ── RBAC: kinotic-server can send email ───────────────────────────────────────

resource "azurerm_role_assignment" "kinotic_server_email_contributor" {
  scope                = azurerm_communication_service.main.id
  role_definition_name = "Contributor"
  principal_id         = azurerm_user_assigned_identity.kinotic_server.principal_id
}

# ── Outputs ───────────────────────────────────────────────────────────────────

output "email_service_endpoint" {
  value = "https://acs-${local.name_prefix}.unitedstates.communication.azure.com"
}

output "email_sender_domain" {
  value = local.global.dns_zone_name
}

output "email_verification_status" {
  value = "az communication email domain show --name ${local.global.dns_zone_name} --email-service-name ecs-${local.name_prefix} --resource-group ${azurerm_resource_group.main.name} --query verificationStates"
}
