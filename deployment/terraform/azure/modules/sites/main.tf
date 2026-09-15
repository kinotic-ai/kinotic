# ── Published UI sites ────────────────────────────────────────────────────────
# Every published UI is served at <label>.<sites_label>.<zone> through one Front Door
# Standard profile, and nothing on Front Door or in DNS changes when a UI is published:
# one wildcard domain *.<sites_label>.<zone> on a wildcard certificate, one wildcard DNS
# record, one route, and a rule set that derives the file's location in the sites storage
# account from the request's host name. A UI is live as soon as its files are under
# sites/<hostname>/ in the account, which the profile reads as its managed identity.
#
# The wildcard certificate is a Let's Encrypt certificate issued here by a DNS challenge on
# the platform zone and kept in the key vault, from which Front Door takes its latest
# version; a terraform apply within min_days_remaining of expiry renews it.

terraform {
  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
    }
    azapi = {
      source = "Azure/azapi"
    }
    acme = {
      source = "vancluever/acme"
    }
    tls = {
      source = "hashicorp/tls"
    }
    random = {
      source = "hashicorp/random"
    }
  }
}

locals {
  sites_domain = "${var.sites_label}.${var.dns_zone_name}"
  # Storage account names allow 3 to 24 lowercase letters and digits
  storage_account_name = substr("st${replace(var.name_prefix, "-", "")}sites", 0, 24)
  # the types the provider admits; wasm and web manifests are not among them
  compressed_content_types = [
    "text/html", "text/css", "text/plain", "text/xml", "text/javascript", "application/javascript",
    "application/x-javascript", "application/json", "application/xml", "image/svg+xml",
  ]
}

# ── Profile and endpoint ──────────────────────────────────────────────────────

resource "azurerm_cdn_frontdoor_profile" "sites" {
  name                = "afd-${var.name_prefix}-sites"
  resource_group_name = var.resource_group_name
  sku_name            = "Standard_AzureFrontDoor"
  tags                = var.tags

  # The profile reads the sites storage account and the certificate's vault as this identity
  identity {
    type = "SystemAssigned"
  }
}

resource "azurerm_cdn_frontdoor_endpoint" "sites" {
  name                     = "sites-${var.name_prefix}"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.sites.id
  tags                     = var.tags
}

# ── Sites storage ─────────────────────────────────────────────────────────────
# One account for every site of the environment: sites/<hostname>/... Anonymous access is
# off; Front Door reads as the profile's identity and kinotic-server writes as its own.

resource "azurerm_storage_account" "sites" {
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
}

resource "azurerm_storage_container" "sites" {
  name                  = "sites"
  storage_account_id    = azurerm_storage_account.sites.id
  container_access_type = "private"
}

resource "azurerm_role_assignment" "frontdoor_reads_sites" {
  scope                = azurerm_storage_account.sites.id
  role_definition_name = "Storage Blob Data Reader"
  principal_id         = azurerm_cdn_frontdoor_profile.sites.identity[0].principal_id
}

resource "azurerm_role_assignment" "server_writes_sites" {
  scope                            = azurerm_storage_account.sites.id
  role_definition_name             = "Storage Blob Data Contributor"
  principal_id                     = var.server_principal_id
  skip_service_principal_aad_check = var.server_principal_skip_aad_check
}

# ── Wildcard certificate ──────────────────────────────────────────────────────

resource "tls_private_key" "acme_account" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "acme_registration" "sites" {
  account_key_pem = tls_private_key.acme_account.private_key_pem
  email_address   = var.lets_encrypt_email
}

resource "random_password" "certificate" {
  length  = 32
  special = false
}

resource "acme_certificate" "sites" {
  account_key_pem = acme_registration.sites.account_key_pem
  common_name     = "*.${local.sites_domain}"
  # Front Door rejects elliptic-curve keys
  key_type                 = "2048"
  min_days_remaining       = 30
  certificate_p12_password = random_password.certificate.result

  dns_challenge {
    provider = "azuredns"
    config = {
      # The roots are applied by a signed-in operator; the challenge writes its TXT record as them
      AZURE_AUTH_METHOD     = "cli"
      AZURE_SUBSCRIPTION_ID = var.dns_zone_subscription_id
      AZURE_RESOURCE_GROUP  = var.dns_zone_resource_group_name
      AZURE_ZONE_NAME       = var.dns_zone_name
    }
  }
}

resource "azurerm_role_assignment" "certificate_officer" {
  scope                = var.key_vault_id
  role_definition_name = "Key Vault Certificates Officer"
  principal_id         = var.certificate_officer_object_id
}

# Role assignments reach the vault's data plane a minute or so after they are written
resource "terraform_data" "wait_for_certificate_officer" {
  input = azurerm_role_assignment.certificate_officer.id
  provisioner "local-exec" {
    command = "sleep 60"
  }
}

resource "azurerm_key_vault_certificate" "sites" {
  name         = "sites-wildcard"
  key_vault_id = var.key_vault_id

  certificate {
    contents = acme_certificate.sites.certificate_p12
    password = random_password.certificate.result
  }

  depends_on = [terraform_data.wait_for_certificate_officer]
}

# Front Door takes the certificate from the vault as the profile's identity
resource "azurerm_role_assignment" "frontdoor_reads_certificate" {
  scope                = var.key_vault_id
  role_definition_name = "Key Vault Certificate User"
  principal_id         = azurerm_cdn_frontdoor_profile.sites.identity[0].principal_id
}

resource "azurerm_role_assignment" "frontdoor_reads_secret" {
  scope                = var.key_vault_id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_cdn_frontdoor_profile.sites.identity[0].principal_id
}

resource "azurerm_cdn_frontdoor_secret" "sites" {
  name                     = "sites-wildcard"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.sites.id

  secret {
    customer_certificate {
      # the versionless id follows renewals
      key_vault_certificate_id = azurerm_key_vault_certificate.sites.versionless_id
    }
  }

  depends_on = [azurerm_role_assignment.frontdoor_reads_certificate, azurerm_role_assignment.frontdoor_reads_secret]
}

# ── Wildcard domain and DNS ───────────────────────────────────────────────────

# Ownership is proven by the certificate: Front Door approves a domain whose customer
# certificate's SAN matches it, so no _dnsauth TXT record is issued or needed
resource "azurerm_cdn_frontdoor_custom_domain" "sites" {
  name                     = "sites-wildcard"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.sites.id
  dns_zone_id              = var.dns_zone_id
  host_name                = "*.${local.sites_domain}"

  tls {
    certificate_type        = "CustomerCertificate"
    cdn_frontdoor_secret_id = azurerm_cdn_frontdoor_secret.sites.id
    minimum_tls_version     = "TLS12"
  }
}

resource "azurerm_dns_cname_record" "sites_wildcard" {
  name                = "*.${var.sites_label}"
  zone_name           = var.dns_zone_name
  resource_group_name = var.dns_zone_resource_group_name
  ttl                 = 300
  record              = azurerm_cdn_frontdoor_endpoint.sites.host_name
}

# ── Named hostnames ───────────────────────────────────────────────────────────
# A hostname of the zone served by the same route, so from sites/<hostname>/ in the account:
# the platform's own UIs where they are not hosted elsewhere. Front Door issues each its
# certificate once the _dnsauth record proves the name.

resource "azurerm_cdn_frontdoor_custom_domain" "named" {
  for_each = var.hostnames

  name                     = each.key
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.sites.id
  dns_zone_id              = var.dns_zone_id
  host_name                = "${each.value}.${var.dns_zone_name}"

  tls {
    certificate_type = "ManagedCertificate"
  }
}

resource "azurerm_dns_txt_record" "named_validation" {
  for_each = var.hostnames

  name                = "_dnsauth.${each.value}"
  zone_name           = var.dns_zone_name
  resource_group_name = var.dns_zone_resource_group_name
  ttl                 = 300

  record {
    value = azurerm_cdn_frontdoor_custom_domain.named[each.key].validation_token
  }
}

resource "azurerm_dns_cname_record" "named" {
  for_each = var.hostnames

  name                = each.value
  zone_name           = var.dns_zone_name
  resource_group_name = var.dns_zone_resource_group_name
  ttl                 = 300
  record              = azurerm_cdn_frontdoor_endpoint.sites.host_name

  depends_on = [azurerm_cdn_frontdoor_route.sites]
}

# ── Origin, rules and route ───────────────────────────────────────────────────
# Origin authentication (the profile's identity presenting a bearer token to the account)
# exists from API version 2025-06-01, which the azurerm provider does not expose yet, so the
# origin group is written as the API's resource. It requires HTTPS health probes; the
# container's properties answer the identity's token with 200.

resource "azapi_resource" "sites_origin_group" {
  type      = "Microsoft.Cdn/profiles/originGroups@2025-06-01"
  name      = "sites"
  parent_id = azurerm_cdn_frontdoor_profile.sites.id

  body = {
    properties = {
      loadBalancingSettings = {
        sampleSize                      = 4
        successfulSamplesRequired       = 3
        additionalLatencyInMilliseconds = 50
      }
      healthProbeSettings = {
        probePath              = "/sites?restype=container"
        probeRequestType       = "HEAD"
        probeProtocol          = "Https"
        probeIntervalInSeconds = 240
      }
      sessionAffinityState = "Disabled"
      authentication = {
        type  = "SystemAssignedIdentity"
        scope = "https://storage.azure.com/.default"
      }
    }
  }

  # the provider's schema predates the authentication property
  schema_validation_enabled = false

  depends_on = [azurerm_role_assignment.frontdoor_reads_sites]
}

resource "azurerm_cdn_frontdoor_origin" "sites" {
  name                           = "blob"
  cdn_frontdoor_origin_group_id  = azapi_resource.sites_origin_group.id
  enabled                        = true
  host_name                      = azurerm_storage_account.sites.primary_blob_host
  origin_host_header             = azurerm_storage_account.sites.primary_blob_host
  http_port                      = 80
  https_port                     = 443
  priority                       = 1
  weight                         = 1000
  certificate_name_check_enabled = true
}

resource "azurerm_cdn_frontdoor_rule_set" "sites" {
  name                     = "sites"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.sites.id
}

# A request naming a file (an extension longer than zero; "Any" also matches a path with no
# extension) is served from the site's directory; url_path expands without its leading slash
resource "azurerm_cdn_frontdoor_rule" "asset" {
  name                      = "asset"
  cdn_frontdoor_rule_set_id = azurerm_cdn_frontdoor_rule_set.sites.id
  order                     = 1
  behavior_on_match         = "Stop"

  conditions {
    url_file_extension_condition {
      operator     = "GreaterThan"
      match_values = ["0"]
    }
  }

  actions {
    url_rewrite_action {
      source_pattern          = "/"
      destination             = "/sites/{hostname}/{url_path}"
      preserve_unmatched_path = false
    }
  }
}

# A request naming no file is a route of the single-page application: its index
resource "azurerm_cdn_frontdoor_rule" "spa" {
  name                      = "spa"
  cdn_frontdoor_rule_set_id = azurerm_cdn_frontdoor_rule_set.sites.id
  order                     = 2
  behavior_on_match         = "Stop"

  conditions {
    url_file_extension_condition {
      operator     = "LessThanOrEqual"
      match_values = ["0"]
    }
  }

  actions {
    url_rewrite_action {
      source_pattern          = "/"
      destination             = "/sites/{hostname}/index.html"
      preserve_unmatched_path = false
    }
  }
}

resource "azurerm_cdn_frontdoor_route" "sites" {
  name                            = "sites"
  cdn_frontdoor_endpoint_id       = azurerm_cdn_frontdoor_endpoint.sites.id
  cdn_frontdoor_origin_group_id   = azapi_resource.sites_origin_group.id
  cdn_frontdoor_origin_ids        = [azurerm_cdn_frontdoor_origin.sites.id]
  cdn_frontdoor_custom_domain_ids = concat([azurerm_cdn_frontdoor_custom_domain.sites.id], [for d in azurerm_cdn_frontdoor_custom_domain.named : d.id])
  cdn_frontdoor_rule_set_ids      = [azurerm_cdn_frontdoor_rule_set.sites.id]
  supported_protocols             = ["Http", "Https"]
  patterns_to_match               = ["/*"]
  # origin authentication requires HTTPS to the origin
  forwarding_protocol    = "HttpsOnly"
  link_to_default_domain = false
  https_redirect_enabled = true
  enabled                = true

  cache {
    query_string_caching_behavior = "IgnoreQueryString"
    compression_enabled           = true
    content_types_to_compress     = local.compressed_content_types
  }
}
