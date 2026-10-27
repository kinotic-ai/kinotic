variable "environment" {
  description = "Names everything this root creates and the <environment>.<cloudflare zone> sites domain; must differ from every developer's dev/ environment"
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
  description = "The portal's label in the platform zone, served by the sites Worker from the sites account: dev-portal for dev-portal.kinotic.ai"
  type        = string
  default     = "dev-portal"
}

variable "console_label" {
  description = "The system console's label in the platform zone, served the same way: dev-console for dev-console.kinotic.ai"
  type        = string
  default     = "dev-console"
}

variable "trusted_ui_hostnames" {
  description = "Hostnames of the UIs built on Kinotic beside the portal and Summit 42 that the management server accepts CORS requests from and returns its sign-in, sign-up and invitation flows to"
  type        = list(string)
  default     = []
}

variable "summit42_enabled" {
  description = "Whether the development server runs Summit 42: its UI on the sites Worker at dev-summit42 in the platform zone, admitted by the management server's CORS pattern, and its server at dev-summit42-api, the proxmox root's summit-42 container"
  type        = bool
  default     = false
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
  description = "The token this root applies the zones with: Workers Scripts edit on the account, DNS, Workers routes and zone settings edit on the customers' zone and the platform zone; with-dev-server-secrets.sh sets it from the operator keychain (proxmox/README.md, Operator secrets)"
  type        = string
  sensitive   = true
}

variable "cloudflare_dns_api_token" {
  description = "A token holding DNS edit on the platform zone and the customers' zone and nothing else, placed on the host for the servers to create organizations' sites records with, for certbot's DNS-01 challenges and for kinotic-dyndns; with-dev-server-secrets.sh sets it from the operator keychain (proxmox/README.md, Operator secrets)"
  type        = string
  sensitive   = true
}
