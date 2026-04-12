# ── Environment ───────────────────────────────────────────────────────────────

variable "environment" {
  description = "Environment name (dev, staging, production)"
  type        = string
}

variable "project" {
  description = "Project name"
  type        = string
}

variable "location" {
  description = "Azure region"
  type        = string
}

variable "kubernetes_version" {
  description = "AKS Kubernetes version"
  type        = string
}

# ── Networking ────────────────────────────────────────────────────────────────

variable "vnet_address_space" { type = list(string) }
variable "aks_subnet_cidr" { type = string }
variable "pod_cidr" { type = string }
variable "service_cidr" { type = string }
variable "dns_service_ip" { type = string }

# ── System Node Pool ──────────────────────────────────────────────────────────

variable "system_node_count" { type = number }
variable "system_vm_size" { type = string }
variable "os_disk_size_gb" { type = number }

# ── Identity / RBAC ──────────────────────────────────────────────────────────

variable "terraform_principal_object_id" {
  description = "Object ID of the principal running Terraform"
  type        = string
  validation {
    condition     = var.terraform_principal_object_id != "00000000-0000-0000-0000-000000000000"
    error_message = "Replace with your actual Azure AD object ID."
  }
}

# ── Deployment Tier ───────────────────────────────────────────────────────────

variable "beta_mode" {
  description = "Beta sizing (shared pool, reduced ES). Set false for production."
  type        = bool
  default     = true
}

# ── Kinotic Server ────────────────────────────────────────────────────────────

variable "kinotic_version" {
  description = "Kinotic server and migration image tag"
  type        = string
}

# ── Load Generator ────────────────────────────────────────────────────────────

variable "enable_load_generator" {
  description = "Deploy the load generator Job"
  type        = bool
  default     = false
}

# ── TLS ───────────────────────────────────────────────────────────────────────

variable "lets_encrypt_email" {
  description = "Email for Let's Encrypt certificate notifications"
  type        = string
}

variable "tls_secret_name" {
  description = "Name of the K8s TLS secret"
  type        = string
  default     = "kinotic-tls"
}

# ── Firecracker ───────────────────────────────────────────────────────────────

variable "enable_firecracker" {
  type    = bool
  default = false
}

variable "firecracker_node_count" {
  type    = number
  default = 1
}

variable "firecracker_vm_size" {
  type    = string
  default = "Standard_D4s_v3"
}

variable "firecracker_subnet_cidr" {
  type    = string
  default = "10.3.0.0/24"
}

variable "firecracker_admin_username" {
  type    = string
  default = "azureuser"
}

variable "firecracker_ssh_public_key" {
  type    = string
  default = ""
}

# ── Tags ──────────────────────────────────────────────────────────────────────

variable "tags" {
  type    = map(string)
  default = {}
}
