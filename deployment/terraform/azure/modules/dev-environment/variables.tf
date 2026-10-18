variable "project" {
  description = "Project name, the first half of every resource name"
  type        = string
}

variable "environment" {
  description = "Names everything this module creates and the apps-<environment> sites domain; unique across every developer and server, since a site hostname is bound to one Front Door profile in all of Azure"
  type        = string
}

variable "location" {
  description = "Azure region"
  type        = string
}

variable "tags" {
  description = "Tags applied to every resource"
  type        = map(string)
  default     = {}
}

variable "dns_zone_name" {
  description = "The platform zone the sites domain is a label of, e.g. kinotic.ai (global/ output dns_zone_name)"
  type        = string
}

variable "dns_zone_id" {
  description = "Resource id of the platform zone (global/ output dns_zone_id)"
  type        = string
}

variable "dns_zone_resource_group_name" {
  description = "Resource group of the platform zone (global/ output resource_group_name)"
  type        = string
}

variable "email_communication_service_id" {
  description = "Resource id of the shared email communication service the server sends through (global/ output email_communication_service_id)"
  type        = string
}

variable "ui_hostnames" {
  description = "Hostnames in the zone served from the sites account on Front Door, for the platform's own UIs: the label within the zone by key, e.g. { portal = \"dev-portal\" }"
  type        = map(string)
  default     = {}
}

variable "cloudflare_zone_name" {
  description = "The Cloudflare zone the environment's sites domain is a label under, e.g. kinotic.io"
  type        = string
}

variable "cloudflare_zone_id" {
  type = string
}

variable "cloudflare_account_id" {
  description = "The Cloudflare account the sites Worker is deployed in"
  type        = string
}

variable "app_server_address" {
  description = "The public IPv4 address the environment's app server is reached at, for the apps-api records, or null on a developer machine with no reachable app server"
  type        = string
  default     = null
}

variable "portal_base_url" {
  description = "Where an organization's own host sends the browser"
  type        = string
}
