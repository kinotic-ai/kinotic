terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }

  backend "azurerm" {
    resource_group_name  = "rg-kinotic-tfstate"
    storage_account_name = "stkinotictfstate"
    container_name       = "tfstate"
    key                  = "frontend/terraform.tfstate"
  }
}

provider "azurerm" {
  features {}
}

# ── Read global state ─────────────────────────────────────────────────────────

data "terraform_remote_state" "global" {
  backend = "azurerm"
  config = {
    resource_group_name  = "rg-kinotic-tfstate"
    storage_account_name = "stkinotictfstate"
    container_name       = "tfstate"
    key                  = "global/terraform.tfstate"
  }
}

locals {
  global = data.terraform_remote_state.global.outputs
}

# ── Static Web App ────────────────────────────────────────────────────────────

resource "azurerm_static_web_app" "portal" {
  name                = "kinotic-portal-${var.environment}"
  resource_group_name = local.global.resource_group_name
  location            = var.location
  sku_tier            = "Free"
  sku_size            = "Free"

  tags = {
    project     = "kinotic"
    environment = var.environment
    managed_by  = "terraform"
  }
}

# ── Custom Domain ─────────────────────────────────────────────────────────────
# Static Web Apps provides free SSL for custom domains automatically.

resource "azurerm_dns_cname_record" "portal" {
  name                = var.subdomain
  zone_name           = local.global.dns_zone_name
  resource_group_name = local.global.resource_group_name
  ttl                 = 300
  record              = azurerm_static_web_app.portal.default_host_name
}

resource "azurerm_static_web_app_custom_domain" "portal" {
  static_web_app_id = azurerm_static_web_app.portal.id
  domain_name       = "${var.subdomain}.${local.global.dns_zone_name}"
  validation_type   = "cname-delegation"

  depends_on = [azurerm_dns_cname_record.portal]
}

# ── Variables ─────────────────────────────────────────────────────────────────

variable "environment" {
  description = "Environment name (production, dev, qa)"
  type        = string
  default     = "production"
}

variable "location" {
  description = "Azure region"
  type        = string
  default     = "centralus"
}

variable "subdomain" {
  description = "Subdomain for the portal (e.g. portal, portal-dev)"
  type        = string
  default     = "portal"
}

variable "frontend_path" {
  description = "Path to the built frontend dist/ folder"
  type        = string
  default     = "../../../../kinotic-frontend/dist"
}

# ── Outputs ───────────────────────────────────────────────────────────────────

output "portal_url" {
  value = "https://${var.subdomain}.${local.global.dns_zone_name}"
}

output "default_hostname" {
  value = azurerm_static_web_app.portal.default_host_name
}

output "deployment_token" {
  description = "Use this token to deploy the SPA: az staticwebapp deploy --deployment-token <token>"
  value       = azurerm_static_web_app.portal.api_key
  sensitive   = true
}
