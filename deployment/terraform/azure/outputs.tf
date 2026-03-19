output "resource_group_name" {
  description = "Name of the resource group"
  value       = azurerm_resource_group.main.name
}

output "vm_public_ip" {
  description = "Public IP address of the VM"
  value       = azurerm_public_ip.main.ip_address
}

output "vm_name" {
  description = "Name of the virtual machine"
  value       = azurerm_linux_virtual_machine.main.name
}

output "private_key_file" {
  description = "Path to the generated private key file"
  value       = local_file.private_key.filename
  sensitive   = false
}

output "ssh_command" {
  description = "SSH command to connect to the VM"
  value       = "ssh -i ${local_file.private_key.filename} ${var.admin_username}@${azurerm_public_ip.main.ip_address}"
}

output "verify_nested_virtualization" {
  description = "Commands to verify nested virtualization is enabled"
  value = <<-EOT
    # Connect to the VM and run:
    ssh -i ${local_file.private_key.filename} ${var.admin_username}@${azurerm_public_ip.main.ip_address}
    
    # Check if virtualization extensions are available:
    grep -c vmx /proc/cpuinfo
    
    # Should return a number > 0. For AMD processors, use:
    grep -c svm /proc/cpuinfo
    
    # Check if KVM is working:
    kvm-ok
    
    # Should indicate "KVM acceleration can be used"
  EOT
}

output "storage_account_name" {
  description = "Name of the storage account for Firecracker images"
  value       = azurerm_storage_account.firecracker_images.name
}

output "storage_container_name" {
  description = "Name of the blob container for Firecracker images"
  value       = azurerm_storage_container.firecracker_images.name
}

output "storage_account_resource_group" {
  description = "Resource group name for the storage account"
  value       = azurerm_resource_group.main.name
}
