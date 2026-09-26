variable "name_prefix" {
  description = "Prefix every resource is named under, e.g. kinotic-production"
  type        = string
}

variable "location" {
  description = "Azure region of the storage account"
  type        = string
}

variable "resource_group_name" {
  description = "Resource group holding the organization storage account"
  type        = string
}

variable "tags" {
  type    = map(string)
  default = {}
}

variable "portal_origins" {
  description = "Origins the portal is served from, e.g. [\"https://portal.kinotic.ai\"], which read files straight from the account"
  type        = list(string)
}

variable "server_principal_id" {
  description = "Principal id of kinotic-server, which signs the SAS URLs files are written and read through"
  type        = string
}

variable "server_principal_skip_aad_check" {
  description = "True for a service principal created in the same apply, which may not have replicated to the RBAC lookup yet"
  type        = bool
  default     = false
}
