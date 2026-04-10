variable "name_prefix" { type = string }
variable "location" { type = string }
variable "resource_group_name" { type = string }
variable "subnet_id" { type = string }
variable "tags" { type = map(string) }

variable "vm_size" {
  description = "VM size — must support nested virtualization (Dv3, Ev3, Dv4, Ev4 series)"
  type        = string
  default     = "Standard_D4s_v3"
}

variable "node_count" {
  description = "Number of Firecracker host VMs to create"
  type        = number
  default     = 1
}

variable "admin_username" {
  description = "SSH admin username"
  type        = string
  default     = "azureuser"
}

variable "ssh_public_key" {
  description = "Path to SSH public key file (generates one if empty)"
  type        = string
  default     = ""
}

# ── SSH Key ───────────────────────────────────────────────────────────────────

resource "tls_private_key" "firecracker" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "random_id" "key_suffix" {
  byte_length = 4
}

resource "random_id" "storage_suffix" {
  byte_length = 4
}

locals {
  ssh_public_key = var.ssh_public_key != "" ? file(var.ssh_public_key) : tls_private_key.firecracker.public_key_openssh

  # Storage account: 3-24 chars, lowercase alphanumeric only
  storage_base = lower(replace(var.name_prefix, "-", ""))
  storage_name = "${substr(local.storage_base, 0, min(length(local.storage_base), 11))}fcimg${random_id.storage_suffix.hex}"
}

# Save private key locally for SSH access
resource "local_file" "private_key" {
  content         = tls_private_key.firecracker.private_key_pem
  filename        = "${path.module}/../../../firecracker-${var.name_prefix}-${random_id.key_suffix.hex}.pem"
  file_permission = "0400"
}

# ── NSG ───────────────────────────────────────────────────────────────────────

resource "azurerm_network_security_group" "firecracker" {
  name                = "nsg-${var.name_prefix}-firecracker"
  location            = var.location
  resource_group_name = var.resource_group_name
  tags                = var.tags

  security_rule {
    name                       = "SSH"
    priority                   = 1001
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "22"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }
}

# ── Public IPs ────────────────────────────────────────────────────────────────

resource "azurerm_public_ip" "firecracker" {
  count               = var.node_count
  name                = "pip-${var.name_prefix}-fc-${count.index}"
  location            = var.location
  resource_group_name = var.resource_group_name
  allocation_method   = "Static"
  sku                 = "Standard"
  tags                = var.tags
}

# ── NICs ──────────────────────────────────────────────────────────────────────

resource "azurerm_network_interface" "firecracker" {
  count               = var.node_count
  name                = "nic-${var.name_prefix}-fc-${count.index}"
  location            = var.location
  resource_group_name = var.resource_group_name
  tags                = var.tags

  ip_configuration {
    name                          = "internal"
    subnet_id                     = var.subnet_id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.firecracker[count.index].id
  }
}

resource "azurerm_network_interface_security_group_association" "firecracker" {
  count                     = var.node_count
  network_interface_id      = azurerm_network_interface.firecracker[count.index].id
  network_security_group_id = azurerm_network_security_group.firecracker.id
}

# ── Storage Account for Firecracker VM Images ────────────────────────────────

resource "azurerm_storage_account" "firecracker_images" {
  name                     = local.storage_name
  resource_group_name      = var.resource_group_name
  location                 = var.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"
  tags                     = var.tags
}

resource "azurerm_storage_container" "firecracker_images" {
  name                  = "firecracker-images"
  storage_account_id    = azurerm_storage_account.firecracker_images.id
  container_access_type = "private"
}

# ── Virtual Machines ──────────────────────────────────────────────────────────

resource "azurerm_linux_virtual_machine" "firecracker" {
  count               = var.node_count
  name                = "${var.name_prefix}-fc-${count.index}"
  location            = var.location
  resource_group_name = var.resource_group_name
  size                = var.vm_size
  admin_username      = var.admin_username
  tags                = var.tags

  # Standard security type (not Trusted Launch) — required for nested virtualization
  disable_password_authentication = true

  network_interface_ids = [
    azurerm_network_interface.firecracker[count.index].id,
  ]

  identity {
    type = "SystemAssigned"
  }

  admin_ssh_key {
    username   = var.admin_username
    public_key = local.ssh_public_key
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "0001-com-ubuntu-server-jammy"
    sku       = "22_04-lts"
    version   = "latest"
  }

  os_disk {
    name                 = "${var.name_prefix}-fc-${count.index}-osdisk"
    caching              = "ReadWrite"
    storage_account_type = "Premium_LRS"
  }

  custom_data = base64encode(templatefile("${path.module}/user_data.sh", {
    admin_username = var.admin_username
  }))
}

# Grant each VM read access to the image storage account
resource "azurerm_role_assignment" "vm_storage_reader" {
  count                = var.node_count
  scope                = azurerm_storage_account.firecracker_images.id
  role_definition_name = "Storage Blob Data Reader"
  principal_id         = azurerm_linux_virtual_machine.firecracker[count.index].identity[0].principal_id
}

# ── Outputs ───────────────────────────────────────────────────────────────────

output "vm_public_ips" {
  value = azurerm_public_ip.firecracker[*].ip_address
}

output "ssh_commands" {
  value = [
    for i in range(var.node_count) :
    "ssh -i ${local_file.private_key.filename} ${var.admin_username}@${azurerm_public_ip.firecracker[i].ip_address}"
  ]
}

output "private_key_file" {
  value = local_file.private_key.filename
}

output "storage_account_name" {
  value = azurerm_storage_account.firecracker_images.name
}
