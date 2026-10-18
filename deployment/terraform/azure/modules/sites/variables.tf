variable "name_prefix" {
  description = "Prefix every resource is named under, e.g. kinotic-production"
  type        = string
}

variable "location" {
  description = "Azure region of the storage account"
  type        = string
}

variable "resource_group_name" {
  description = "Resource group holding the sites storage account and, with named hostnames, the Front Door profile"
  type        = string
}

variable "tags" {
  type    = map(string)
  default = {}
}

variable "dns_zone_name" {
  description = "The platform zone the named hostnames are in, e.g. kinotic.ai"
  type        = string
}

variable "dns_zone_id" {
  type = string
}

variable "dns_zone_resource_group_name" {
  type = string
}

variable "server_principal_id" {
  description = "Principal id the Kinotic servers run as; the system server publishes sites into the storage account"
  type        = string
}

variable "server_principal_skip_aad_check" {
  description = "True for a service principal created in the same apply, which may not have replicated to the RBAC lookup yet"
  type        = bool
  default     = false
}

variable "hostnames" {
  description = "Hostnames of the platform zone served from the account the way a published site is, sites/<hostname>/, on Front Door managed certificates: the label within the zone by a key of the caller's choosing, e.g. { portal = \"dev-portal\" }. Empty creates no Front Door profile"
  type        = map(string)
  default     = {}
}
