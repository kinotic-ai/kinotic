# ── Organization storage ──────────────────────────────────────────────────────
# One account for the files the platform keeps on behalf of organizations, partitioned by
# organization and then by use: organizations/<organizationId>/<use>/..., today
# organizations/<organizationId>/sboms/<projectId>.cdx.json for each project's SBOM. Anonymous
# access is off. kinotic-server signs short-lived SAS URLs, each for one directory or one file,
# through which workloads write and the portal reads, so the account answers the portal's origins.

terraform {
  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
    }
  }
}

locals {
  # Storage account names allow 3 to 24 lowercase letters and digits
  storage_account_name = substr("st${replace(var.name_prefix, "-", "")}orgs", 0, 24)
}

resource "azurerm_storage_account" "organizations" {
  name                            = local.storage_account_name
  resource_group_name             = var.resource_group_name
  location                        = var.location
  account_kind                    = "StorageV2"
  account_tier                    = "Standard"
  account_replication_type        = "LRS"
  is_hns_enabled                  = true
  min_tls_version                 = "TLS1_2"
  allow_nested_items_to_be_public = false
  tags                            = var.tags

  blob_properties {
    # The portal reads a file through a read SAS the server issued it
    cors_rule {
      allowed_origins    = var.portal_origins
      allowed_methods    = ["GET", "HEAD"]
      allowed_headers    = ["*"]
      exposed_headers    = ["*"]
      max_age_in_seconds = 3600
    }
  }
}

resource "azurerm_storage_container" "organizations" {
  name                  = "organizations"
  storage_account_id    = azurerm_storage_account.organizations.id
  container_access_type = "private"
}

# The role also grants the user delegation keys the server signs the SAS URLs with
resource "azurerm_role_assignment" "server_manages_organizations" {
  scope                            = azurerm_storage_account.organizations.id
  role_definition_name             = "Storage Blob Data Contributor"
  principal_id                     = var.server_principal_id
  skip_service_principal_aad_check = var.server_principal_skip_aad_check
}
