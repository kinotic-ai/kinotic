# ── Environment ───────────────────────────────────────────────────────────────

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
}

variable "project" {
  description = "Project name, used as prefix for all resources"
  type        = string
}

variable "location" {
  description = "Azure region"
  type        = string
}

variable "kubernetes_version" {
  description = "AKS Kubernetes version (e.g. 1.29)"
  type        = string
}

# ── Networking ────────────────────────────────────────────────────────────────

variable "vnet_address_space" {
  description = "VNet address space"
  type        = list(string)
}

variable "aks_subnet_cidr" {
  description = "AKS subnet CIDR"
  type        = string
}

variable "pod_cidr" {
  description = "Pod CIDR for Azure CNI overlay"
  type        = string
}

variable "service_cidr" {
  description = "Kubernetes service CIDR"
  type        = string
}

variable "dns_service_ip" {
  description = "Kubernetes DNS service IP (must be within service_cidr)"
  type        = string
}

# ── System Node Pool ──────────────────────────────────────────────────────────

variable "system_node_count" {
  description = "Number of system pool nodes"
  type        = number
}

variable "system_vm_size" {
  description = "VM size for system pool nodes"
  type        = string
}

variable "os_disk_size_gb" {
  description = "OS disk size in GB for system pool nodes"
  type        = number
}

# ── Identity / RBAC ──────────────────────────────────────────────────────────

variable "terraform_principal_object_id" {
  description = "Object ID of the principal running Terraform (user or SP)"
  type        = string

  validation {
    condition     = var.terraform_principal_object_id != "00000000-0000-0000-0000-000000000000"
    error_message = "Replace terraform_principal_object_id with your actual Azure AD object ID."
  }
}

# ── Deployment Tier ───────────────────────────────────────────────────────────

variable "beta_mode" {
  description = "Use beta sizing (single ES node on shared system pool, smaller resources). Set false for production topology."
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
  description = "Deploy the load generator Job after kinotic-server is ready"
  type        = bool
  default     = false
}

# ── Grafana / Entra ID ────────────────────────────────────────────────────────

variable "disable_grafana_entra" {
  description = "Disable Azure Entra ID login for Grafana (falls back to local admin/admin)"
  type        = bool
  default     = false
}

# ── TLS / DNS ─────────────────────────────────────────────────────────────────

variable "domain_name" {
  description = "Domain name for the Azure DNS zone and Let's Encrypt certificate"
  type        = string
  default     = "kinotic.ai"
}

variable "lets_encrypt_email" {
  description = "Email for Let's Encrypt certificate notifications"
  type        = string
}

variable "tls_secret_name" {
  description = "Name of the Kubernetes TLS secret created by cert-manager"
  type        = string
  default     = "kinotic-tls"
}

# ── Firecracker ───────────────────────────────────────────────────────────────

variable "enable_firecracker" {
  description = "Deploy Firecracker VM host(s) for customer workloads"
  type        = bool
  default     = false
}

variable "firecracker_node_count" {
  description = "Number of Firecracker host VMs"
  type        = number
  default     = 1
}

variable "firecracker_vm_size" {
  description = "VM size for Firecracker hosts (must support nested virtualization)"
  type        = string
  default     = "Standard_D4s_v3"
}

variable "firecracker_subnet_cidr" {
  description = "CIDR for the Firecracker host subnet"
  type        = string
  default     = "10.3.0.0/24"
}

variable "firecracker_admin_username" {
  description = "SSH admin username for Firecracker VMs"
  type        = string
  default     = "azureuser"
}

variable "firecracker_ssh_public_key" {
  description = "Path to SSH public key file (generates one if empty)"
  type        = string
  default     = ""
}

# ── Tags ──────────────────────────────────────────────────────────────────────

variable "tags" {
  description = "Tags applied to all resources"
  type        = map(string)
  default     = {}
}
