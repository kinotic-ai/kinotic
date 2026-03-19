# ========================================
# Provider Configuration
# ========================================

provider "azurerm" {
  features {}
}

# ========================================
# Resource Group
# ========================================

resource "azurerm_resource_group" "main" {
  name     = var.resource_group_name
  location = var.location

  tags = {
    Name        = var.vm_name
    Environment = "development"
  }
}

# ========================================
# SSH Key Management
# ========================================

resource "tls_private_key" "vm_ssh" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "random_id" "key_suffix" {
  byte_length = 4
}

resource "random_id" "storage_suffix" {
  byte_length = 4
}

# Use provided public key if specified, otherwise use generated key
locals {
  ssh_public_key_content = var.ssh_public_key != "" ? file(var.ssh_public_key) : tls_private_key.vm_ssh.public_key_openssh
  
  # Storage account name: must be 3-24 chars, lowercase alphanumeric only
  # Format: {sanitized-vm-name}fcimg{8-char-random-suffix}
  # Truncate vm_name part to max 11 chars to leave room for "fcimg" (5) + random hex (8) = 24 total
  storage_account_base = lower(replace(var.vm_name, "-", ""))
  storage_account_name = length(local.storage_account_base) > 11 ? "${substr(local.storage_account_base, 0, 11)}fcimg${random_id.storage_suffix.hex}" : "${local.storage_account_base}fcimg${random_id.storage_suffix.hex}"
}

# Save private key to local file
resource "local_file" "private_key" {
  content         = tls_private_key.vm_ssh.private_key_pem
  filename        = "${path.module}/azure-vm-key-${random_id.key_suffix.hex}.pem"
  file_permission = "0400"
}

# ========================================
# Network Infrastructure
# ========================================

# Virtual Network
resource "azurerm_virtual_network" "main" {
  name                = "${var.vm_name}-vnet"
  address_space       = [var.vnet_cidr]
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name

  tags = {
    Name = "${var.vm_name}-vnet"
  }
}

# Subnet
resource "azurerm_subnet" "main" {
  name                 = "${var.vm_name}-subnet"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.subnet_cidr]
}

# Public IP
resource "azurerm_public_ip" "main" {
  name                = "${var.vm_name}-pip"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  allocation_method   = "Static"
  sku                 = "Standard"

  tags = {
    Name = "${var.vm_name}-pip"
  }
}

# Network Security Group
resource "azurerm_network_security_group" "main" {
  name                = "${var.vm_name}-nsg"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name

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

  tags = {
    Name = "${var.vm_name}-nsg"
  }
}

# Network Interface
resource "azurerm_network_interface" "main" {
  name                = "${var.vm_name}-nic"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name

  ip_configuration {
    name                          = "internal"
    subnet_id                     = azurerm_subnet.main.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.main.id
  }

  tags = {
    Name = "${var.vm_name}-nic"
  }
}

# Associate NSG with NIC
resource "azurerm_network_interface_security_group_association" "main" {
  network_interface_id      = azurerm_network_interface.main.id
  network_security_group_id = azurerm_network_security_group.main.id
}

# ========================================
# Storage Account for Firecracker Images
# ========================================

resource "azurerm_storage_account" "firecracker_images" {
  name                     = local.storage_account_name
  resource_group_name      = azurerm_resource_group.main.name
  location                 = azurerm_resource_group.main.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"

  tags = {
    Name = "${var.vm_name}-firecracker-images"
  }
}

resource "azurerm_storage_container" "firecracker_images" {
  name                  = "firecracker-images"
  storage_account_name  = azurerm_storage_account.firecracker_images.name
  container_access_type = "private"
}

# ========================================
# Virtual Machine
# ========================================


resource "azurerm_linux_virtual_machine" "main" {
  name                = var.vm_name
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  size                = var.vm_size
  admin_username      = var.admin_username

  # Critical: Use Standard security type (not Trusted Launch) for nested virtualization
  # Don't set security_type to ensure Standard security type (default)
  disable_password_authentication = true

  network_interface_ids = [
    azurerm_network_interface.main.id,
  ]

  # Enable managed identity for blob storage access
  identity {
    type = "SystemAssigned"
  }

  admin_ssh_key {
    username   = var.admin_username
    public_key = local.ssh_public_key_content
  }

  # Image must support nested virtualization
  source_image_reference {
    publisher = "Canonical"
    offer     = "0001-com-ubuntu-server-jammy"
    sku       = "22_04-lts"
    version   = "latest"
  }

  os_disk {
    name                 = "${var.vm_name}-osdisk"
    caching              = "ReadWrite"
    storage_account_type = "Premium_LRS"
  }

  # User data script to install and configure KVM
  # Pass admin username to the script via template
  custom_data = base64encode(templatefile("${path.module}/user_data.sh", {
    admin_username = var.admin_username
  }))

  tags = {
    Name = var.vm_name
  }
}

# Assign Storage Blob Data Reader role to VM's managed identity
# This allows the VM to read from the blob storage container
resource "azurerm_role_assignment" "vm_storage_reader" {
  scope                = azurerm_storage_account.firecracker_images.id
  role_definition_name = "Storage Blob Data Reader"
  principal_id         = azurerm_linux_virtual_machine.main.identity[0].principal_id
}

# Note: Azure VMs with nested virtualization support are automatically enabled
# when using supported VM sizes (Dv3, Ev3 series). The user_data script will
# install and configure KVM and Firecracker on the VM after it boots.
# 
# Firecracker kernel and rootfs images should be uploaded to the blob storage
# container by an external process. The VM has Storage Blob Data Reader permissions
# to access these images.
