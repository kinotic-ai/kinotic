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
  description = "The API's label in the platform zone: dev-api for dev-api.kinotic.ai, the name the router's public address answers to"
  type        = string
  default     = "dev-api"
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
  description = "The public IPv4 address the router forwards 443 from, as the API record's first value; kinotic-dyndns.timer on the host keeps the record current afterwards"
  type        = string
}

variable "lets_encrypt_email" {
  description = "Account email for the Let's Encrypt registration that issues the sites wildcard certificate; set it in local.auto.tfvars"
  type        = string
}
