output "storage_blob_endpoint" {
  description = "The blob endpoint kinotic-server issues organization storage URLs on"
  value       = azurerm_storage_account.organizations.primary_blob_endpoint
}

output "storage_account_id" {
  description = "The organization storage account, for roles a caller adds"
  value       = azurerm_storage_account.organizations.id
}
