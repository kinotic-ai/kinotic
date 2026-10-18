variable "environment" {
  description = "Names everything this root creates and the apps-<environment> sites domain; must differ from every developer's dev/ environment"
  type        = string
  default     = "dev"
}

variable "project" {
  description = "Project name"
  type        = string
  default     = "kinotic"
}

variable "location" {
  description = "Azure region"
  type        = string
  default     = "centralus"
}

variable "api_label" {
  description = "The management server's label in the platform zone: dev-api for dev-api.kinotic.ai, the API of the portal, the CLI and MCP hosts, and the GitHub App's webhook"
  type        = string
  default     = "dev-api"
}

variable "system_api_label" {
  description = "The system server's label in the platform zone: dev-system-api for dev-system-api.kinotic.ai, the system console's API"
  type        = string
  default     = "dev-system-api"
}

variable "portal_label" {
  description = "The portal's label in the platform zone, served by Front Door from the sites account"
  type        = string
  default     = "dev-portal"
}

variable "console_label" {
  description = "The system console's label in the platform zone, served by Front Door from the sites account"
  type        = string
  default     = "dev-console"
}

variable "public_ip" {
  description = "The public IPv4 address the router forwards 443 from, as the first value of every server's record; kinotic-dyndns.timer on the host keeps the records current afterwards"
  type        = string
}

variable "cloudflare_zone_name" {
  description = "The Cloudflare zone the sites domain <environment>.<zone> is under"
  type        = string
  default     = "kinotic.io"
}

variable "cloudflare_zone_id" {
  description = "The zone's id; set it in local.auto.tfvars"
  type        = string
}

variable "cloudflare_account_id" {
  description = "The Cloudflare account the sites Worker is deployed in; set it in local.auto.tfvars"
  type        = string
}

variable "cloudflare_api_token" {
  description = "The token this root applies the zone with: Workers Scripts edit on the account, DNS and zone settings edit on the zone; set it in local.auto.tfvars"
  type        = string
  sensitive   = true
}

variable "cloudflare_dns_api_token" {
  description = "A token holding DNS edit on the zone and nothing else, placed on the host for the servers to create organizations' sites records with; set it in local.auto.tfvars"
  type        = string
  sensitive   = true
}
