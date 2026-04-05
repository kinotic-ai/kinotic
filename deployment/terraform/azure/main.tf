terraform {
  required_version = ">= 1.7"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.110"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 2.50"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.0"
    }
  }

  # ── Remote state ─────────────────────────────────────────────────────────────
  # Bootstrap first: ./bootstrap-state.sh
  backend "azurerm" {
    resource_group_name  = "rg-kinotic-tfstate"
    storage_account_name = "stkinotictfstate"
    container_name       = "tfstate"
    key                  = "aks/terraform.tfstate"
  }
}

provider "azurerm" {
  features {
    resource_group {
      # Prevents accidental deletion of non-empty resource groups
      prevent_deletion_if_contains_resources = true
    }
    key_vault {
      purge_soft_delete_on_destroy    = false
      recover_soft_deleted_key_vaults = true
    }
  }
}

# ── Locals ────────────────────────────────────────────────────────────────────
locals {
  name_prefix = "${var.project}-${var.environment}"

  common_tags = merge(var.tags, {
    environment = var.environment
    project     = var.project
    managed_by  = "terraform"
  })
}

# ── Resource Group ────────────────────────────────────────────────────────────
resource "azurerm_resource_group" "main" {
  name     = "rg-${local.name_prefix}"
  location = var.location
  tags     = local.common_tags
}

# ── Identity Module ───────────────────────────────────────────────────────────
module "identity" {
  source = "./modules/identity"

  name_prefix                   = local.name_prefix
  location                      = var.location
  resource_group_name           = azurerm_resource_group.main.name
  tags                          = local.common_tags
  terraform_principal_object_id = var.terraform_principal_object_id
}

# ── Networking Module ─────────────────────────────────────────────────────────
module "networking" {
  source = "./modules/networking"

  name_prefix         = local.name_prefix
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  vnet_address_space  = var.vnet_address_space
  aks_subnet_cidr     = var.aks_subnet_cidr
  tags                = local.common_tags

  # Grant the cluster identity Network Contributor on the subnet
  # so it can attach/detach Azure Load Balancers automatically
  aks_identity_principal_id = module.identity.kubelet_identity_principal_id

  # Firecracker subnet — created only when enable_firecracker = true
  enable_firecracker      = var.enable_firecracker
  firecracker_subnet_cidr = var.firecracker_subnet_cidr
}

# ── AKS Module ────────────────────────────────────────────────────────────────
module "aks" {
  source = "./modules/aks"

  name_prefix            = local.name_prefix
  location               = var.location
  resource_group_name    = azurerm_resource_group.main.name
  kubernetes_version     = var.kubernetes_version
  dns_prefix             = "${local.name_prefix}-aks"

  # Identity
  control_plane_identity_id   = module.identity.control_plane_identity_id
  kubelet_identity_id         = module.identity.kubelet_identity_id
  kubelet_identity_client_id  = module.identity.kubelet_identity_client_id
  kubelet_identity_object_id  = module.identity.kubelet_identity_object_id

  # Networking
  aks_subnet_id  = module.networking.aks_subnet_id
  pod_cidr       = var.pod_cidr
  service_cidr   = var.service_cidr
  dns_service_ip = var.dns_service_ip

  # System node pool
  system_node_count = var.system_node_count
  system_vm_size    = var.system_vm_size
  os_disk_size_gb   = var.os_disk_size_gb

  beta_mode = var.beta_mode

  tags = local.common_tags
}
