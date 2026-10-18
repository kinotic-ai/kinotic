variable "zone_id" {
  description = "The Cloudflare zone the sites domain is in, e.g. the id of kinotic.io"
  type        = string
}

variable "zone_name" {
  description = "The zone's name, e.g. kinotic.io"
  type        = string
}

variable "sites_subdomain" {
  description = "The label the environment's sites domain is under within the zone, or empty for the zone itself: empty for kinotic.io in production, dev for dev.kinotic.io on the development server, a developer's own name on their machine"
  type        = string
  default     = ""
}

variable "apps_api_label" {
  description = "The label the application API hosts are under within the sites domain: apps-api for <org>--<app>.apps-api.<sites domain>"
  type        = string
  default     = "apps-api"
}

variable "app_server_address" {
  description = "The public IPv4 address the app server is reached at, which the apps-api records point at, proxied; null for an environment with no reachable app server, which gets no apps-api records"
  type        = string
  default     = null
}

variable "account_id" {
  description = "The Cloudflare account the Worker is deployed in"
  type        = string
}

variable "worker_name" {
  description = "The name of the sites Worker in the account, unique per environment"
  type        = string
}

variable "sites_storage_blob_endpoint" {
  description = "The blob endpoint of the sites storage account the Worker serves from, e.g. https://stkinoticsites.blob.core.windows.net"
  type        = string
}

variable "portal_base_url" {
  description = "Where an organization's own host sends the browser, e.g. https://portal.kinotic.ai"
  type        = string
}
