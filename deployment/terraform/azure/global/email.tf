# ── Azure Communication Services (Email) ──────────────────────────────────────
# Fully automated: creates ACS, email service, custom domain, all DNS
# verification records (domain, SPF, DKIM, DKIM2), and triggers verification.
# No manual steps after terraform apply.
#
# Lives in global because the email service is shared across all clusters and
# bound to the global DNS zone. Per-cluster RBAC (granting kinotic-server
# identity send permission) lives in the cluster terraform.

resource "azurerm_email_communication_service" "main" {
  name                = "ecs-${var.project}"
  resource_group_name = azurerm_resource_group.global.name
  data_location       = "United States"
  tags                = local.common_tags
}

resource "azurerm_email_communication_service_domain" "kinotic" {
  name              = azurerm_dns_zone.main.name
  email_service_id  = azurerm_email_communication_service.main.id
  domain_management = "CustomerManaged"
  tags              = local.common_tags
}

resource "azurerm_communication_service" "main" {
  name                = "acs-${var.project}"
  resource_group_name = azurerm_resource_group.global.name
  data_location       = "United States"
  tags                = local.common_tags
}

# Link email domain to communication service. The azurerm provider does not
# expose linkedDomains, so we patch via az CLI. `az communication email domain
# link` does not exist in CLI; the property lives on the communication service
# itself and is set via `az communication update --linked-domains`.
resource "terraform_data" "link_email_domain" {
  triggers_replace = [
    azurerm_communication_service.main.id,
    azurerm_email_communication_service_domain.kinotic.id,
  ]

  provisioner "local-exec" {
    command = <<-EOT
      set -euo pipefail
      az communication update \
        --name "acs-${var.project}" \
        --resource-group "${azurerm_resource_group.global.name}" \
        --linked-domains "${azurerm_email_communication_service_domain.kinotic.id}"
    EOT
  }

  depends_on = [
    azurerm_communication_service.main,
    azurerm_email_communication_service_domain.kinotic,
  ]
}

# ── DNS Records (all automated from verification_records output) ──────────────

# Domain-ownership verification + SPF share the apex TXT record set. Azure DNS
# stores multiple TXT values under one record set; each ACS verification query
# pulls the value it needs. Splitting these into two `azurerm_dns_txt_record`
# resources causes them to overwrite each other (same Azure resource ID).
#
# ACS' verification_records returns `name = "kinotic.ai"` for the apex, but
# `azurerm_dns_txt_record` needs `"@"` for the apex — passing the literal zone
# name creates a subdomain record at `kinotic.ai.kinotic.ai`, where ACS won't
# find it. Translate when the returned name matches the zone.
locals {
  email_domain_record_name = (
    azurerm_email_communication_service_domain.kinotic.verification_records[0].domain[0].name == azurerm_dns_zone.main.name
    ? "@"
    : azurerm_email_communication_service_domain.kinotic.verification_records[0].domain[0].name
  )
}

resource "azurerm_dns_txt_record" "email_apex" {
  name                = local.email_domain_record_name
  zone_name           = azurerm_dns_zone.main.name
  resource_group_name = azurerm_resource_group.global.name
  ttl                 = azurerm_email_communication_service_domain.kinotic.verification_records[0].domain[0].ttl

  record {
    value = azurerm_email_communication_service_domain.kinotic.verification_records[0].domain[0].value
  }
  record {
    value = azurerm_email_communication_service_domain.kinotic.verification_records[0].spf[0].value
  }
}

moved {
  from = azurerm_dns_txt_record.email_domain
  to   = azurerm_dns_txt_record.email_apex
}

# DKIM record (CNAME)
resource "azurerm_dns_cname_record" "email_dkim" {
  name                = azurerm_email_communication_service_domain.kinotic.verification_records[0].dkim[0].name
  zone_name           = azurerm_dns_zone.main.name
  resource_group_name = azurerm_resource_group.global.name
  ttl                 = azurerm_email_communication_service_domain.kinotic.verification_records[0].dkim[0].ttl
  record              = azurerm_email_communication_service_domain.kinotic.verification_records[0].dkim[0].value
}

# DKIM2 record (CNAME)
resource "azurerm_dns_cname_record" "email_dkim2" {
  name                = azurerm_email_communication_service_domain.kinotic.verification_records[0].dkim2[0].name
  zone_name           = azurerm_dns_zone.main.name
  resource_group_name = azurerm_resource_group.global.name
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
          --name "${azurerm_dns_zone.main.name}" \
          --email-service-name "ecs-${var.project}" \
          --resource-group "${azurerm_resource_group.global.name}" \
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
    azurerm_dns_txt_record.email_apex,
    azurerm_dns_cname_record.email_dkim,
    azurerm_dns_cname_record.email_dkim2,
  ]
}

# ── Outputs ───────────────────────────────────────────────────────────────────

output "email_communication_service_id" {
  value = azurerm_communication_service.main.id
}

output "email_service_endpoint" {
  value = "https://acs-${var.project}.unitedstates.communication.azure.com"
}

output "email_sender_domain" {
  value = azurerm_dns_zone.main.name
}

output "email_verification_status" {
  value = "az communication email domain show --name ${azurerm_dns_zone.main.name} --email-service-name ecs-${var.project} --resource-group ${azurerm_resource_group.global.name} --query verificationStates"
}
