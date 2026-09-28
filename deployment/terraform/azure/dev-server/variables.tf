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
  description = "The org server's label in the platform zone: dev-api for dev-api.kinotic.ai, the API of the portal, the CLI and MCP hosts, and the GitHub App's webhook"
  type        = string
  default     = "dev-api"
}

variable "system_api_label" {
  description = "The system server's label in the platform zone: dev-system-api for dev-system-api.kinotic.ai, the system console's API"
  type        = string
  default     = "dev-system-api"
}

variable "apps_api_label" {
  description = "The app server's label in the platform zone: dev-apps-api for dev-apps-api.kinotic.ai, the name runtime workloads dial, under which every application's API host is a label (<organizationId>--<applicationId>.dev-apps-api.kinotic.ai)"
  type        = string
  default     = "dev-apps-api"
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

variable "lets_encrypt_email" {
  description = "Account email for the Let's Encrypt registration that issues the sites wildcard certificate; set it in local.auto.tfvars"
  type        = string
}
