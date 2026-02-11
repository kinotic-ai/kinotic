variable "location" {
  description = "Azure region for resources"
  type        = string
  default     = "eastus"
}

variable "resource_group_name" {
  description = "Name of the resource group"
  type        = string
  default     = "kinotic-rg"
}

variable "vm_name" {
  description = "Name of the virtual machine"
  type        = string
  default     = "kinotic-vm"
}

variable "vm_size" {
  description = "VM size (must support nested virtualization, e.g., Standard_D4s_v3, Standard_D8s_v3, Standard_E4s_v3)"
  type        = string
  default     = "Standard_D4s_v3"
}

variable "admin_username" {
  description = "Administrator username for the VM"
  type        = string
  default     = "azureuser"
}

variable "ubuntu_version" {
  description = "Ubuntu Server version (sku)"
  type        = string
  default     = "22_04-lts"
}

variable "ssh_public_key" {
  description = "Path to SSH public key file (optional, will generate if not provided)"
  type        = string
  default     = ""
}

variable "subnet_cidr" {
  description = "CIDR block for the subnet"
  type        = string
  default     = "10.0.1.0/24"
}

variable "vnet_cidr" {
  description = "CIDR block for the virtual network"
  type        = string
  default     = "10.0.0.0/16"
}
