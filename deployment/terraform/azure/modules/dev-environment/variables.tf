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

variable "lets_encrypt_email" {
  description = "Account email for the Let's Encrypt registration that issues the sites wildcard certificate"
  type        = string
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

variable "dns_zone_subscription_id" {
  description = "Subscription of the platform zone (global/ output subscription_id)"
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
