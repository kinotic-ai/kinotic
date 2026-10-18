# ── The sites storage account ─────────────────────────────────────────────────
# One account for every published site of the environment, sites/<hostname>/..., which the
# system server's publish workloads write through upload URLs it signs and the Cloudflare sites
# Worker (deployment/terraform/azure/modules/cloudflare-sites) reads over the public blob
# endpoint: the files are the public web content of the sites, so the container allows
# anonymous reads of its blobs and nothing else.
#
# Named hostnames of the platform's own zone, such as a development server's portal and
# console, are served from the same account by a Front Door Standard profile on managed
# certificates; the profile exists only when a hostname is asked for.

terraform {
  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
    }
    azapi = {
      source = "Azure/azapi"
    }
  }
}

locals {
  # Storage account names allow 3 to 24 lowercase letters and digits
  storage_account_name = substr("st${replace(var.name_prefix, "-", "")}sites", 0, 24)
  front_door           = length(var.hostnames) > 0 ? 1 : 0
  # the types the provider admits; wasm and web manifests are not among them
  compressed_content_types = [
    "text/html", "text/css", "text/plain", "text/xml", "text/javascript", "application/javascript",
    "application/x-javascript", "application/json", "application/xml", "image/svg+xml",
  ]
}

# ── Sites storage ─────────────────────────────────────────────────────────────

resource "azurerm_storage_account" "sites" {
  name                            = local.storage_account_name
  resource_group_name             = var.resource_group_name
  location                        = var.location
  account_kind                    = "StorageV2"
  account_tier                    = "Standard"
  account_replication_type        = "LRS"
  is_hns_enabled                  = true
  min_tls_version                 = "TLS1_2"
  allow_nested_items_to_be_public = true
  tags                            = var.tags
}

# Blob-level anonymous read: a site's files are fetched by the edge by name, and the container
# itself cannot be listed
resource "azurerm_storage_container" "sites" {
  name                  = "sites"
  storage_account_id    = azurerm_storage_account.sites.id
  container_access_type = "blob"
}

resource "azurerm_role_assignment" "server_writes_sites" {
  scope                            = azurerm_storage_account.sites.id
  role_definition_name             = "Storage Blob Data Contributor"
  principal_id                     = var.server_principal_id
  skip_service_principal_aad_check = var.server_principal_skip_aad_check
}

# ── Named hostnames on Front Door ─────────────────────────────────────────────
# A hostname of the platform's zone served from sites/<hostname>/ in the account: the
# platform's own UIs where they are not hosted elsewhere. Front Door issues each its
# certificate once the _dnsauth record proves the name.

resource "azurerm_cdn_frontdoor_profile" "sites" {
  count = local.front_door

  name                = "afd-${var.name_prefix}-sites"
  resource_group_name = var.resource_group_name
  sku_name            = "Standard_AzureFrontDoor"
  tags                = var.tags

  # The profile reads the sites storage account as this identity
  identity {
    type = "SystemAssigned"
  }
}

resource "azurerm_cdn_frontdoor_endpoint" "sites" {
  count = local.front_door

  name                     = "sites-${var.name_prefix}"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.sites[0].id
  tags                     = var.tags
}

resource "azurerm_role_assignment" "frontdoor_reads_sites" {
  count = local.front_door

  scope                = azurerm_storage_account.sites.id
  role_definition_name = "Storage Blob Data Reader"
  principal_id         = azurerm_cdn_frontdoor_profile.sites[0].identity[0].principal_id
}

resource "azurerm_cdn_frontdoor_custom_domain" "named" {
  for_each = var.hostnames

  name                     = each.key
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.sites[0].id
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
  record              = azurerm_cdn_frontdoor_endpoint.sites[0].host_name

  depends_on = [azurerm_cdn_frontdoor_route.sites]
}

# ── Origin, rules and route ───────────────────────────────────────────────────
# Origin authentication (the profile's identity presenting a bearer token to the account)
# exists from API version 2025-06-01, which the azurerm provider does not expose yet, so the
# origin group is written as the API's resource. It requires HTTPS health probes; the
# container's properties answer the identity's token with 200.

resource "azapi_resource" "sites_origin_group" {
  count = local.front_door

  type      = "Microsoft.Cdn/profiles/originGroups@2025-06-01"
  name      = "sites"
  parent_id = azurerm_cdn_frontdoor_profile.sites[0].id

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
  count = local.front_door

  name                           = "blob"
  cdn_frontdoor_origin_group_id  = azapi_resource.sites_origin_group[0].id
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
  count = local.front_door

  name                     = "sites"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.sites[0].id
}

# A request naming a file (an extension longer than zero; "Any" also matches a path with no
# extension) is served from the site's directory; url_path expands without its leading slash
resource "azurerm_cdn_frontdoor_rule" "asset" {
  count = local.front_door

  name                      = "asset"
  cdn_frontdoor_rule_set_id = azurerm_cdn_frontdoor_rule_set.sites[0].id
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
  count = local.front_door

  name                      = "spa"
  cdn_frontdoor_rule_set_id = azurerm_cdn_frontdoor_rule_set.sites[0].id
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
  count = local.front_door

  name                            = "sites"
  cdn_frontdoor_endpoint_id       = azurerm_cdn_frontdoor_endpoint.sites[0].id
  cdn_frontdoor_origin_group_id   = azapi_resource.sites_origin_group[0].id
  cdn_frontdoor_origin_ids        = [azurerm_cdn_frontdoor_origin.sites[0].id]
  cdn_frontdoor_custom_domain_ids = [for d in azurerm_cdn_frontdoor_custom_domain.named : d.id]
  cdn_frontdoor_rule_set_ids      = [azurerm_cdn_frontdoor_rule_set.sites[0].id]
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
