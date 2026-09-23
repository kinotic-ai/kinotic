variable "name_prefix" {
  description = "Prefix every resource is named under, e.g. kinotic-production"
  type        = string
}

variable "location" {
  description = "Azure region of the storage account and the key vault certificate"
  type        = string
}

variable "resource_group_name" {
  description = "Resource group holding the profile and the sites storage account"
  type        = string
}

variable "tags" {
  type    = map(string)
  default = {}
}

variable "dns_zone_name" {
  description = "The platform zone, e.g. kinotic.ai"
  type        = string
}

variable "dns_zone_id" {
  type = string
}

variable "dns_zone_resource_group_name" {
  type = string
}

variable "dns_zone_subscription_id" {
  type = string
}

variable "sites_label" {
  description = "The label of the sites domain within the zone: apps for apps.kinotic.ai, apps-local for a developer's apps-local.kinotic.ai"
  type        = string
}

variable "key_vault_id" {
  description = "RBAC-enabled key vault the wildcard certificate is issued into; must be in the profile's subscription"
  type        = string
}

variable "certificate_officer_object_id" {
  description = "Object id of the principal running terraform, which imports the certificate into the vault"
  type        = string
}

variable "lets_encrypt_email" {
  description = "Account email for the Let's Encrypt registration that issues the wildcard certificate"
  type        = string
}

variable "server_principal_id" {
  description = "Principal id of kinotic-server, which publishes sites into the storage account"
  type        = string
}

variable "server_principal_skip_aad_check" {
  description = "True for a service principal created in the same apply, which may not have replicated to the RBAC lookup yet"
  type        = bool
  default     = false
}

variable "hostnames" {
  description = "Hostnames in the zone served from the account the way a published site is, sites/<hostname>/, on Front Door managed certificates: the label within the zone by a key of the caller's choosing, e.g. { portal = \"dev-portal\" }"
  type        = map(string)
  default     = {}
}
