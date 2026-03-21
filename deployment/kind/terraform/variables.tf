# ── Cluster ────────────────────────────────────────────────

variable "cluster_name" {
  description = "KinD cluster name"
  type        = string
  default     = "kinotic-cluster"
}

variable "node_image" {
  description = "KinD node image (controls Kubernetes version). Leave empty for latest."
  type        = string
  default     = ""
}

variable "worker_count" {
  description = "Number of KinD worker nodes"
  type        = number
  default     = 3
}

# ── Kinotic Server ────────────────────────────────────────

variable "kinotic_version" {
  description = "Kinotic server image tag (published to Docker Hub)"
  type        = string
  default     = "latest"
}

# ── Feature Flags ─────────────────────────────────────────

variable "enable_keycloak" {
  description = "Deploy Keycloak + PostgreSQL for OIDC authentication"
  type        = bool
  default     = false
}

variable "enable_load_generator" {
  description = "Run load generator Job after deployment"
  type        = bool
  default     = false
}

# ── Credentials (sensitive, with dev defaults) ────────────

variable "keycloak_db_username" {
  description = "PostgreSQL username for Keycloak"
  type        = string
  default     = "keycloak"
  sensitive   = true
}

variable "keycloak_db_password" {
  description = "PostgreSQL password for Keycloak"
  type        = string
  default     = "keycloak"
  sensitive   = true
}

variable "keycloak_admin_password" {
  description = "Keycloak admin console password"
  type        = string
  default     = "admin"
  sensitive   = true
}

# ── Timeouts ──────────────────────────────────────────────

variable "deploy_timeout" {
  description = "Helm release timeout in seconds"
  type        = number
  default     = 600
}
