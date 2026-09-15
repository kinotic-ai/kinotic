# ── Proxmox ───────────────────────────────────────────────────────────────────

variable "proxmox_host" {
  description = "The Proxmox host's address, for the API and for SSH as root (uploads, and the container applier)"
  type        = string
}

variable "proxmox_password" {
  description = "root@pam's password: bind mounts into containers are allowed for that user alone, and an API token authenticates as root@pam!name, which fails the check. Set it in local.auto.tfvars or PROXMOX_VE_PASSWORD"
  type        = string
  sensitive   = true
}

variable "proxmox_insecure" {
  description = "Skip verification of the API's TLS certificate, which the installer self-signs"
  type        = bool
  default     = true
}

variable "proxmox_node" {
  description = "The node everything runs on"
  type        = string
  default     = "pve"
}

variable "vm_datastore_id" {
  description = "Datastore for the container root filesystems"
  type        = string
  default     = "local-zfs"
}

variable "files_datastore_id" {
  description = "Datastore the OCI images (vztmpl) and the uploaded manifests, config files, and applier (snippets) go to; host/prepare-host.sh enables both content types"
  type        = string
  default     = "local"
}

variable "snippets_dir" {
  description = "Where that datastore keeps snippets on the host"
  type        = string
  default     = "/var/lib/vz/snippets"
}

# ── Network ───────────────────────────────────────────────────────────────────

variable "bridge" {
  description = "The LAN bridge: the server, Loki, Tempo, Mimir and Grafana attach to it"
  type        = string
  default     = "vmbr0"
}

variable "private_network" {
  description = "The name of the SDN zone and VNet the Elasticsearch nodes, the migration, and the server's second interface attach to: a bridge with no physical port that the host gateways and source-NATs"
  type        = string
  default     = "kinotic"
  validation {
    condition     = can(regex("^[a-z][a-z0-9]{0,7}$", var.private_network))
    error_message = "A VNet name is at most 8 lowercase alphanumerics starting with a letter."
  }
}

variable "private_cidr" {
  description = "The private network; the host takes .1, the ES nodes .11 to .13, the server .20, the migration .21"
  type        = string
  default     = "10.10.0.0/24"
}

variable "server_ip" {
  description = "kinotic-server's LAN address in CIDR notation; the router forwards 443 and 58503 here, and workloads on the node VM dial it"
  type        = string
}

variable "loki_ip" {
  description = "Loki's LAN address in CIDR notation"
  type        = string
}

variable "tempo_ip" {
  description = "Tempo's LAN address in CIDR notation"
  type        = string
}

variable "mimir_ip" {
  description = "Mimir's LAN address in CIDR notation"
  type        = string
}

variable "grafana_ip" {
  description = "Grafana's LAN address in CIDR notation"
  type        = string
}

variable "gateway" {
  description = "The LAN's default gateway"
  type        = string
}

variable "dns_servers" {
  description = "Resolvers for every container, and the resolver the nodes give every workload"
  type        = list(string)
}

# ── Disks and directories ─────────────────────────────────────────────────────

variable "es_data_dirs" {
  description = "Host directories the three Elasticsearch nodes keep their data in, one physical disk each: the ZFS datasets host/prepare-host.sh creates"
  type        = list(string)
  default     = ["/es1/data", "/es2/data", "/es3/data"]
  validation {
    condition     = length(var.es_data_dirs) == 3
    error_message = "Three Elasticsearch data directories, one per node: quorum survives one disk only with three master-eligible nodes."
  }
}

variable "data_dir" {
  description = "Host directory the other containers' state and config live under"
  type        = string
  default     = "/var/lib/kinotic"
}

variable "secrets_dir" {
  description = "Host directory the operator places secrets in (sync-secrets.sh); the applier merges them, terraform never reads them"
  type        = string
  default     = "/etc/kinotic/secrets"
}

# ── Images ────────────────────────────────────────────────────────────────────

variable "kinotic_version" {
  description = "Tag of the kinotic-server and kinotic-migration images"
  type        = string
  default     = "5.0.0-SNAPSHOT"
}

variable "elasticsearch_version" {
  type    = string
  default = "9.5.1"
}

variable "loki_version" {
  type    = string
  default = "3.4.2"
}

variable "tempo_version" {
  type    = string
  default = "2.6.1"
}

variable "mimir_version" {
  type    = string
  default = "2.14.3"
}

variable "grafana_version" {
  type    = string
  default = "12.3.1"
}

# ── Sizing ────────────────────────────────────────────────────────────────────

variable "server_cores" {
  type    = number
  default = 4
}

variable "server_memory_mb" {
  type    = number
  default = 4096
}

variable "es_cores" {
  type    = number
  default = 2
}

variable "es_memory_mb" {
  description = "Per node; half of it is heap"
  type        = number
  default     = 4096
}

variable "azure_state_path" {
  description = "The dev-server Azure root's state file, whose outputs configure the server"
  type        = string
  default     = "../azure/dev-server/terraform.tfstate"
}
